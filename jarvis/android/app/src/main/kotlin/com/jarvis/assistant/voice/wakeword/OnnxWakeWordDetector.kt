package com.jarvis.assistant.voice.wakeword

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.jarvis.assistant.voice.MicController
import com.jarvis.assistant.voice.VoiceDiagnostics
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlin.math.sqrt
import kotlin.math.min

/**
 * Offline wake-word detector — Phase 4 rebuild.
 *
 * Adds a TEMPORAL GATE: instead of firing on a single score above threshold,
 * the detector requires [WakeWordConfig.temporalPositiveCount] positives out of
 * the last [WakeWordConfig.temporalWindowSize] inference windows, each scoring
 * at least [WakeWordConfig.minConfidenceForPositive].
 *
 * Single inference pipeline:
 *   feedPcm()  → ring buffer append
 *               → mel spectrogram
 *               → embeddings
 *               → classifier score
 *               → temporal gate
 *               → cooldown
 *               → WakeWordListener.onWakeWordDetected()
 *
 * Phase 5 fix: processAndDetect() is REMOVED.
 * LiveKitWakeWordEngine calls feedPcm() once per audio frame and reads the
 * returned score directly — no second inference.
 */
class OnnxWakeWordDetector(
    private val context: Context? = null,
    private val config: WakeWordConfig = WakeWordConfig(),
    private val micController: MicController = MicController(context)
) : WakeWordDetector {

    companion object {
        private const val TAG = "OnnxWakeWordDetector"

        // Front-end contract (verified against upstream models).
        const val SAMPLE_RATE = 16000
        const val N_MELS = 32
        const val EMBEDDING_WINDOW = 76
        const val EMBEDDING_STRIDE = 8
        const val MIN_EMBEDDINGS = 16
        const val EMBEDDING_DIM = 96

        const val ASSET_DIR = "wakeword"
        const val MEL_MODEL = "melspectrogram.onnx"
        const val EMB_MODEL = "embedding_model.onnx"
        const val CLS_MODEL = "hey_jarvis.onnx"

        // Rolling PCM ring buffer: 2.5 s @ 16 kHz covers the 2 s window + slack.
        const val PCM_BUFFER_SAMPLES = (SAMPLE_RATE * 2.5).toInt()
        // Classifier is evaluated on a 2.0 s slice.
        const val CLASSIFY_WINDOW_SAMPLES = SAMPLE_RATE * 2

        // Phase 10: adaptive noise gate — fixed floor replaced by calibration.
        // Base minimum RMS; the adaptive gate raises this dynamically.
        private const val BASE_AUDIO_RMS = 0.005f

        const val MEL_INPUT  = "input"
        const val EMB_INPUT  = "input_1"
        const val CLS_INPUT  = "input"
        const val CLS_OUTPUT = "score"
    }

    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var melSession: OrtSession? = null
    private var embSession: OrtSession? = null
    private var clsSession: OrtSession? = null

    @Volatile private var available = false

    // Rolling int16 PCM ring buffer (single writer from the capture thread).
    private val pcmRing = ShortArray(PCM_BUFFER_SAMPLES)
    @Volatile private var pcmWritePos = 0
    @Volatile private var pcmFilled  = 0

    private var listener: WakeWordListener? = null
    private val cooldown = WakeCooldown(config.cooldownMs)

    // ── Phase 4: Temporal gate ───────────────────────────────────────────────
    // Circular buffer of recent scores for temporal majority vote.
    private val scoreWindow = FloatArray(config.temporalWindowSize) { 0f }
    private var scoreWindowIdx = 0

    // ── Phase 10: Adaptive noise gate ───────────────────────────────────────
    // Calibrated noise floor updated on startup and during silence windows.
    @Volatile private var noiseFloor = BASE_AUDIO_RMS
    private var calibrationSamples = 0
    private var calibrationRmsSum = 0.0
    private val calibrationTarget = 30  // number of silent windows to calibrate
    @Volatile private var calibrated = false

    private val threshold: Float
        get() = WakeWordConfig.thresholdForSensitivity(config.sensitivity)

    override fun isAvailable(): Boolean = available

    fun setSensitivity(sensitivity: Float) {
        config.sensitivity = sensitivity.coerceIn(0f, 1f)
    }

    init { loadModels() }

    private fun loadModels() {
        val ctx = context ?: run {
            Log.w(TAG, "No context — cannot load ONNX assets")
            return
        }
        try {
            val am: AssetManager = ctx.assets
            melSession = newSession(am, "$ASSET_DIR/$MEL_MODEL")
            embSession = newSession(am, "$ASSET_DIR/$EMB_MODEL")
            clsSession = newSession(am, "$ASSET_DIR/$CLS_MODEL")
            available = melSession != null && embSession != null && clsSession != null
            Log.i(TAG, "ONNX wake-word models loaded (available=$available)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ONNX wake-word models — offline detection disabled", e)
            available = false
        }
    }

    private fun newSession(am: AssetManager, assetPath: String): OrtSession? {
        am.open(assetPath).use { stream ->
            val bytes = stream.readBytes()
            if (bytes.isEmpty()) return null
            return ortEnv.createSession(bytes)
        }
    }

    /**
     * Feed a chunk of raw 16 kHz mono int16 PCM.
     *
     * Phase 5: This is the SINGLE inference call per audio frame.
     * Returns the classifier score (0..1), or null when the window is too short,
     * the RMS gate rejects the frame, or the models are unavailable.
     *
     * Also applies the temporal gate and fires [WakeWordListener] when accepted.
     *
     * Thread-safe: called exclusively from the capture thread.
     */
    @Synchronized
    fun feedPcm(samples: ShortArray, offset: Int, length: Int): Float? {
        if (!available) return null

        // 1. Append to rolling ring buffer.
        var o = offset
        var n = length
        while (n > 0) {
            val space = PCM_BUFFER_SAMPLES - pcmWritePos
            val take  = min(space, n)
            System.arraycopy(samples, o, pcmRing, pcmWritePos, take)
            pcmWritePos = (pcmWritePos + take) % PCM_BUFFER_SAMPLES
            pcmFilled   = min(pcmFilled + take, PCM_BUFFER_SAMPLES)
            o += take
            n -= take
        }
        if (pcmFilled < CLASSIFY_WINDOW_SAMPLES) return null

        // 2. Extract last 2.0 s as float32.
        val window = FloatArray(CLASSIFY_WINDOW_SAMPLES)
        val startIdx = (pcmWritePos - CLASSIFY_WINDOW_SAMPLES + PCM_BUFFER_SAMPLES) % PCM_BUFFER_SAMPLES
        for (i in 0 until CLASSIFY_WINDOW_SAMPLES) {
            window[i] = pcmRing[(startIdx + i) % PCM_BUFFER_SAMPLES] / 32768.0f
        }

        // 3. Phase 10: Adaptive noise gate.
        val rms = sqrt(window.sumOf { (it * it).toDouble() } / window.size).toFloat()
        val dynamicThreshold = noiseFloor * 2.5f  // signal must be 2.5× noise floor
        if (!calibrated) {
            // Calibration phase: sample quiet-ish windows to estimate noise floor.
            if (rms < BASE_AUDIO_RMS * 10f) {
                calibrationRmsSum += rms
                calibrationSamples++
                if (calibrationSamples >= calibrationTarget) {
                    noiseFloor = (calibrationRmsSum / calibrationSamples).toFloat().coerceAtLeast(BASE_AUDIO_RMS)
                    calibrated = true
                    Log.i(TAG, "Noise floor calibrated: %.4f".format(noiseFloor))
                }
            }
            // During calibration use fixed base floor.
            if (rms < BASE_AUDIO_RMS) return null
        } else {
            if (rms < dynamicThreshold.coerceAtLeast(BASE_AUDIO_RMS)) {
                // Below noise floor — update floor estimate and skip inference.
                noiseFloor = (noiseFloor * 0.98f + rms * 0.02f).coerceAtLeast(BASE_AUDIO_RMS)
                return null
            }
        }

        // 4. Mel spectrogram.
        val mel = runMel(window) ?: return null

        // 5. Embeddings.
        val embeddings = runEmbeddings(mel) ?: return null
        if (embeddings.size < MIN_EMBEDDINGS * EMBEDDING_DIM) return null
        val last16 = embeddings.copyOfRange(
            embeddings.size - MIN_EMBEDDINGS * EMBEDDING_DIM,
            embeddings.size
        )

        // 6. Classifier score.
        val score = runClassifier(last16)

        // 7. Phase 4: Temporal gate.
        scoreWindow[scoreWindowIdx] = score
        scoreWindowIdx = (scoreWindowIdx + 1) % config.temporalWindowSize

        val positiveCount = scoreWindow.count { it >= config.minConfidenceForPositive }
        val temporalAccept = positiveCount >= config.temporalPositiveCount

        // Diagnostic log for every candidate.
        val decision = if (temporalAccept && score >= threshold && cooldown.allow()) "ACCEPT" else "REJECT"
        val rejectReason = when {
            score < config.minConfidenceForPositive -> "LOW_SCORE(${"%.3f".format(score)})"
            !temporalAccept -> "TEMPORAL_GATE(hits=$positiveCount/${config.temporalPositiveCount})"
            score < threshold -> "BELOW_THRESHOLD(${"%.3f".format(score)}<${"%.3f".format(threshold)})"
            else -> "COOLDOWN"
        }
        VoiceDiagnostics.logWakeCandidate(
            score = score,
            threshold = threshold,
            positiveHits = positiveCount,
            windowSize = config.temporalWindowSize,
            rms = rms,
            noiseFloor = noiseFloor,
            decision = decision,
            rejectReason = if (decision == "ACCEPT") null else rejectReason
        )

        if (decision == "ACCEPT") {
            Log.i(TAG, "Wake word ACCEPTED (score=${"%".format(score)}, hits=$positiveCount/${config.temporalPositiveCount})")
            // Reset temporal window after acceptance to prevent immediate re-trigger.
            scoreWindow.fill(0f)
            listener?.onWakeWordDetected()
        }

        return score
    }

    private fun runMel(audio: FloatArray): FloatArray? {
        val session = melSession ?: return null
        val shape = longArrayOf(1, audio.size.toLong())
        val tensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(audio), shape)
        return try {
            session.run(mapOf(MEL_INPUT to tensor)).use { result ->
                val flat = resultToFloatArray(result) ?: return null
                if (flat.size % N_MELS != 0) return null
                FloatArray(flat.size) { flat[it] / 10.0f + 2.0f }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Mel spectrogram inference failed", e)
            null
        } finally {
            tensor.close()
        }
    }

    private fun resultToFloatArray(result: OrtSession.Result): FloatArray? {
        val tensor = result[0] as? OnnxTensor ?: return null
        val buf = tensor.floatBuffer ?: return null
        val n = buf.remaining()
        if (n <= 0) return null
        val arr = FloatArray(n)
        buf.get(arr)
        return arr
    }

    private fun runEmbeddings(mel: FloatArray): FloatArray? {
        val session = embSession ?: return null
        val frames = mel.size / N_MELS
        if (frames < EMBEDDING_WINDOW) return null
        val nWindows = (frames - EMBEDDING_WINDOW) / EMBEDDING_STRIDE + 1
        if (nWindows <= 0) return null

        val input = FloatArray(nWindows * EMBEDDING_WINDOW * N_MELS)
        var p = 0
        for (w in 0 until nWindows) {
            val frameStart = w * EMBEDDING_STRIDE
            for (r in 0 until EMBEDDING_WINDOW) {
                val melBase = (frameStart + r) * N_MELS
                for (c in 0 until N_MELS) {
                    input[p++] = mel[melBase + c]
                }
            }
        }
        val shape = longArrayOf(nWindows.toLong(), EMBEDDING_WINDOW.toLong(), N_MELS.toLong(), 1)
        val tensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(input), shape)
        return try {
            session.run(mapOf(EMB_INPUT to tensor)).use { result ->
                val flat = resultToFloatArray(result) ?: return null
                if (flat.size != nWindows * EMBEDDING_DIM) {
                    Log.w(TAG, "Embedding size ${flat.size} != expected ${nWindows * EMBEDDING_DIM}")
                    return@use null
                }
                flat
            }
        } catch (e: Exception) {
            Log.e(TAG, "Embedding inference failed", e)
            null
        } finally {
            tensor.close()
        }
    }

    private fun runClassifier(last16: FloatArray): Float {
        val session = clsSession ?: return 0f
        val shape = longArrayOf(1, MIN_EMBEDDINGS.toLong(), EMBEDDING_DIM.toLong())
        val tensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(last16), shape)
        return try {
            session.run(mapOf(CLS_INPUT to tensor)).use { result ->
                resultToFloatArray(result)?.firstOrNull() ?: 0f
            }
        } catch (e: Exception) {
            Log.e(TAG, "Classifier inference failed", e)
            0f
        } finally {
            tensor.close()
        }
    }

    override fun setListener(listener: WakeWordListener) { this.listener = listener }

    override fun start() { if (!available) loadModels() }
    override fun stop()  { /* capture driven by engine */ }
    override fun pause() { /* capture paused by engine */ }
    override fun resume(){ /* capture resumed by engine */ }

    override fun release() {
        listener = null
        try { melSession?.close() } catch (_: Exception) {}
        try { embSession?.close() } catch (_: Exception) {}
        try { clsSession?.close() } catch (_: Exception) {}
        melSession = null
        embSession = null
        clsSession = null
        available = false
    }
}
