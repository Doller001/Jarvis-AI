package com.jarvis.assistant.voice.wakeword

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import com.jarvis.assistant.voice.MicController

/**
 * Continuous, low-power offline wake-word engine.
 *
 * Owns the microphone as a Single Mic Owner ("WakeWordEngine") and runs a
 * 16 kHz mono [AudioRecord] capture loop, feeding raw PCM frames to an
 * [OnnxWakeWordDetector]. When the detector fires above threshold (and passes
 * cooldown), the engine releases the mic and invokes [onWake].
 *
 * The command path ([com.jarvis.assistant.voice.SpeechController]) must call
 * [pause] before taking the mic and [resume] when done — matching the
 * Single Mic Owner contract used everywhere in JARVIS.
 */
class LiveKitWakeWordEngine(
    private val context: Context? = null,
    private val config: WakeWordConfig = WakeWordConfig(),
    private val detector: OnnxWakeWordDetector = OnnxWakeWordDetector(context, config),
    private val micController: MicController = MicController(context)
) {
    companion object {
        private const val TAG = "LiveKitWakeWordEngine"
        private const val OWNER_TAG = "WakeWordEngine"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val FRAME_SAMPLES = 320 // 20 ms @ 16 kHz
    }

    @Volatile
    private var isMonitoring = false

    @Volatile
    private var pausedForCommand = false

    private var captureThread: Thread? = null
    private var audioRecord: AudioRecord? = null
    private val stateLock = Any()

    private var onWakeCallback: ((String?) -> Unit)? = null
    private var onErrorCallback: ((Throwable) -> Unit)? = null

    val isAvailable: Boolean get() = detector.isAvailable()
    val isMonitoringNow: Boolean get() = isMonitoring

    /** Live-update the detection sensitivity (0..1) without restarting capture. */
    fun setSensitivity(sensitivity: Float) {
        detector.setSensitivity(sensitivity)
    }

    fun setOnWakeListener(onWake: (String?) -> Unit) {
        onWakeCallback = onWake
    }

    fun setOnErrorListener(onError: (Throwable) -> Unit) {
        onErrorCallback = onError
    }

    fun startMonitoring() {
        if (isMonitoring) return
        if (!detector.isAvailable()) {
            val msg = "Wake-word ONNX models missing — offline detection unavailable"
            Log.w(TAG, msg)
            onErrorCallback?.invoke(IllegalStateException(msg))
            return
        }
        if (!micController.hasPermission()) {
            val msg = "RECORD_AUDIO permission missing for wake-word listening"
            Log.w(TAG, msg)
            onErrorCallback?.invoke(IllegalStateException(msg))
            return
        }
        detector.setListener(object : WakeWordListener {
            override fun onWakeWordDetected() {
                if (!isMonitoring || pausedForCommand) return
                Log.i(TAG, "Wake word detected — handing off to command mode")
                onWakeCallback?.invoke(null)
            }

            override fun onWakeWordError(error: Throwable) {
                Log.e(TAG, "Wake-word detector error", error)
                onErrorCallback?.invoke(error)
            }
        })
        detector.start()
        isMonitoring = true
        pausedForCommand = false
        startCaptureThread()
        Log.i(TAG, "Wake-word monitoring started (Hey Jarvis)")
    }

    private fun startCaptureThread() {
        synchronized(stateLock) {
            captureThread = Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
                val bufSize = (FRAME_SAMPLES * 2 * 4).coerceAtLeast(minBuf)
                try {
                    audioRecord = AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        SAMPLE_RATE, CHANNEL, ENCODING, bufSize
                    )
                    if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                        Log.e(TAG, "AudioRecord init failed for wake-word capture")
                        releaseAudioRecord()
                        return@Thread
                    }
                    audioRecord?.startRecording()
                    val buffer = ShortArray(FRAME_SAMPLES)
                    while (isMonitoring && !pausedForCommand) {
                        val read = audioRecord?.read(buffer, 0, FRAME_SAMPLES) ?: -1
                        if (read > 0) {
                            val slice = if (read == FRAME_SAMPLES) buffer else buffer.copyOf(read)
                            detector.feedPcm(slice, 0, slice.size)
                            // Evaluate the rolling buffer and fire the listener
                            // if the score clears the (sensitivity) threshold.
                            detector.processAndDetect()
                        } else if (read < 0) {
                            Log.w(TAG, "Wake-word AudioRecord read error: $read")
                            Thread.sleep(10)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Wake-word capture loop error", e)
                } finally {
                    releaseAudioRecord()
                }
            }, "Jarvis-WakeWordCapture").also {
                it.priority = Thread.MAX_PRIORITY
                it.start()
            }
        }
    }

    private fun releaseAudioRecord() {
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
    }

    /** Hands the mic to the command recognizer. */
    fun pause() {
        if (!isMonitoring) return
        pausedForCommand = true
        releaseAudioRecord()
        detector.pause()
        Log.i(TAG, "Wake-word paused — command mode owns the microphone")
    }

    /** Returns the mic to wake-word listening. */
    fun resume() {
        if (!isMonitoring || !pausedForCommand) return
        pausedForCommand = false
        detector.resume()
        startCaptureThread()
        Log.i(TAG, "Wake-word resumed")
    }

    fun stopMonitoring() {
        isMonitoring = false
        pausedForCommand = false
        captureThread?.interrupt()
        captureThread?.join(1000)
        captureThread = null
        releaseAudioRecord()
        detector.stop()
        Log.i(TAG, "Wake-word monitoring stopped")
    }

    fun release() {
        stopMonitoring()
        detector.release()
        onWakeCallback = null
        onErrorCallback = null
        Log.i(TAG, "Wake-word engine released")
    }
}
