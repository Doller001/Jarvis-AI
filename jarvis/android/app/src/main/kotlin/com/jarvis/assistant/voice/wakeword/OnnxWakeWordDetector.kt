package com.jarvis.assistant.voice.wakeword

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.jarvis.assistant.voice.MicController
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlin.math.min

/**
 * Offline wake-word detector ported from livekit-wakeword's Python pipeline
 * (src/livekit/wakeword/inference/model.py + feature_extractor.py).
 *
 * The pipeline runs THREE frozen ONNX models via ONNX Runtime Mobile:
 *   1. melspectrogram.onnx  : 16 kHz PCM (1, samples) -> mel (1, frames, 32)
 *   2. embedding_model.onnx : (N, 76, 32, 1) mel windows -> (N, 96) embeddings
 *   3. hey_jarvis.onnx       : (1, 16, 96) -> sigmoid score (1, 1)
 *
 * I/O names and shapes were verified empirically against the upstream models
 * (see jarvis/wakeword-training + .tmp_livekit/contract_test.py):
 *   mel input  : "input"      (1, samples) float32
 *   mel output : (1, 1, F, 32) -> squeeze channel -> (1, F, 32)
 *   post-proc  : mel / 10.0 + 2.0
 *   emb input  : "input_1"    (N, 76, 32, 1) float32
 *   emb output : (N, 1, 1, 96) -> squeeze -> (N, 96)
 *   classifier : "embeddings" (1, 16, 96) -> "score" (1, 1)
 *
 * Audio is captured continuously at 16 kHz mono int16 by [LiveKitWakeWordEngine],
 * which feeds raw PCM windows here.
 */
class OnnxWakeWordDetector(
    private val context: Context? = null,
    private val config: WakeWordConfig = WakeWordConfig(),
    private val micController: MicController = MicController(context)
) : WakeWordDetector {

    companion object {
        private const val TAG = "OnnxWakeWordDetector"
        private const val OWNER_TAG = "WakeWordDetector"

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
        // Classifier is evaluated on a 2.0 s slice (the canonical window).
        const val CLASSIFY_WINDOW_SAMPLES = SAMPLE_RATE * 2

        const val MEL_INPUT = "input"
        const val EMB_INPUT = "input_1"
        const val CLS_INPUT = "embeddings"
        const val CLS_OUTPUT = "score"

        // Empty PCM sentinel so processAndDetect() can re-evaluate the buffer
        // without receiving new audio (feedPcm with length 0 is a no-op append).
        private val EMPTY_SHORT = ShortArray(0)
    }

    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var melSession: OrtSession? = null
    private var embSession: OrtSession? = null
    private var clsSession: OrtSession? = null

    @Volatile
    private var available = false

    // Rolling int16 PCM buffer (single writer from the capture thread).
    private val pcmRing = ShortArray(PCM_BUFFER_SAMPLES)
    @Volatile
    private var pcmWritePos = 0
    @Volatile
    private var pcmFilled = 0

    private var listener: WakeWordListener? = null
    private val cooldown = WakeCooldown(config.cooldownMs)

    private val threshold: Float
        get() = WakeWordConfig.thresholdForSensitivity(config.sensitivity)

    override fun isAvailable(): Boolean = available

    /** Live-update sensitivity (0..1); takes effect on the next classification. */
    fun setSensitivity(sensitivity: Float) {
        config.sensitivity = sensitivity.coerceIn(0f, 1f)
    }

    init {
        loadModels()
    }

    private fun loadModels() {
        val ctx = context
        if (ctx == null) {
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
     * Feed a chunk of raw 16 kHz mono int16 PCM. Returns the classifier score
     * (0..1) for this window, or null if the window is too short / models absent.
     * Thread-safe: called from the capture thread.
     */
    @Synchronized
    fun feedPcm(samples: ShortArray, offset: Int, length: Int): Float? {
        if (!available) return null

        // 1. Append to rolling ring buffer.
        var o = offset
        var n = length
        while (n > 0) {
            val space = PCM_BUFFER_SAMPLES - pcmWritePos
            val take = min(space, n)
            System.arraycopy(samples, o, pcmRing, pcmWritePos, take)
            pcmWritePos = (pcmWritePos + take) % PCM_BUFFER_SAMPLES
            pcmFilled = min(pcmFilled + take, PCM_BUFFER_SAMPLES)
            o += take
            n -= take
        }
        if (pcmFilled < CLASSIFY_WINDOW_SAMPLES) return null

        // 2. Extract the most recent CLASSIFY_WINDOW_SAMPLES (2.0 s) of PCM
        //    into a contiguous float array (int16 -> float32 / 32768).
        val window = FloatArray(CLASSIFY_WINDOW_SAMPLES)
        val startIdx = (pcmWritePos - CLASSIFY_WINDOW_SAMPLES + PCM_BUFFER_SAMPLES) % PCM_BUFFER_SAMPLES
        for (i in 0 until CLASSIFY_WINDOW_SAMPLES) {
            val s = pcmRing[(startIdx + i) % PCM_BUFFER_SAMPLES]
            window[i] = s / 32768.0f
        }

        // 3. Mel spectrogram (1, samples) -> (1, F, 32).
        val mel = runMel(window) ?: return null

        // 4. Build sliding-window embeddings (window 76, stride 8), keep last 16.
        val embeddings = runEmbeddings(mel) ?: return null
        if (embeddings.size < MIN_EMBEDDINGS * EMBEDDING_DIM) return null

        val last16 = embeddings.copyOfRange(
            embeddings.size - MIN_EMBEDDINGS * EMBEDDING_DIM,
            embeddings.size
        )

        // 5. Classifier -> score.
        return runClassifier(last16)
    }

    private fun runMel(audio: FloatArray): FloatArray? {
        val session = melSession ?: return null
        val shape = longArrayOf(1, audio.size.toLong())
        val tensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(audio), shape)
        return try {
            session.run(emptyMap(), mapOf(MEL_INPUT to tensor)).use { result ->
                val flat = resultToFloatArray(result) ?: return null
                // Output is row-major (1,1,F,32); the two leading 1-dims add
                // nothing, so `flat` is already (F*32) in (F,32) layout.
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

    /**
     * Read the first float output of an ORT result as a contiguous FloatArray.
     * Works regardless of the tensor rank because ORT stores float tensors in
     * row-major (flat) order — we reshape using KNOWN dims, never nested-array
     * walking (which is brittle across ORT Java return shapes).
     */
    private fun resultToFloatArray(result: OrtSession.Result): FloatArray? {
        // OrtSession.Result.get(int) returns an OnnxValue (the first output).
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

        // Build (nWindows, 76, 32, 1) input.
        val input = FloatArray(nWindows * EMBEDDING_WINDOW * N_MELS * 1)
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
            session.run(emptyMap(), mapOf(EMB_INPUT to tensor)).use { result ->
                val flat = resultToFloatArray(result) ?: return null
                // Output is row-major (N,1,1,96) -> already (N*96) with the
                // 96-dim embedding contiguous per window.
                if (flat.size != nWindows * EMBEDDING_DIM) {
                    Log.w(TAG, "Embedding output size ${flat.size} != expected ${nWindows * EMBEDDING_DIM}")
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
            session.run(emptyMap(), mapOf(CLS_INPUT to tensor)).use { result ->
                val flat = resultToFloatArray(result)
                flat?.firstOrNull() ?: 0f
            }
        } catch (e: Exception) {
            Log.e(TAG, "Classifier inference failed", e)
            0f
        } finally {
            tensor.close()
        }
    }

    override fun setListener(listener: WakeWordListener) {
        this.listener = listener
    }

    /** Evaluate the current buffer and fire the listener if above threshold. */
    @Synchronized
    fun processAndDetect(): Boolean {
        if (!available) return false
        val score = feedPcm(EMPTY_SHORT, 0, 0) ?: return false
        if (score >= threshold && cooldown.allow()) {
            Log.i(TAG, "Wake word detected (score=%.3f, threshold=%.3f)".format(score, threshold))
            listener?.onWakeWordDetected()
            return true
        }
        return false
    }

    override fun start() {
        // The engine drives capture; here we just ensure models are ready.
        if (!available) loadModels()
    }

    override fun stop() {
        // No persistent capture here.
    }

    override fun pause() {
        // Capture paused by the engine; nothing to release.
    }

    override fun resume() {
        // Capture resumed by the engine.
    }

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
