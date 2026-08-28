package com.jarvis.assistant.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight interrupt detector that runs during TTS playback.
 *
 * Detection pipeline:
 *   1. Capture short audio windows (200ms)
 *   2. Check audio energy for speech presence
 *   3. Fire interrupt callback when speech detected (with cooldown)
 *
 * Does NOT run full SpeechRecognizer — keeps latency minimal.
 * Full keyword matching is handled by VoiceRuntime when interrupt is confirmed.
 */
class InterruptDetector(
    private val micController: MicController,
    private val vadEngine: VadEngine? = null
) {

    companion object {
        private const val TAG = "InterruptDetector"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val FRAME_MS = 200
        private const val FRAME_SAMPLES = SAMPLE_RATE * FRAME_MS / 1000  // 3200
        private const val ENERGY_THRESHOLD = 300.0
        private const val SPEECH_CONFIRM_FRAMES = 2  // 400ms of speech
        private const val COOLDOWN_MS = 800L
        private const val MAX_SPEECH_FRAMES = 15  // 3s max
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var isRunning = false
    @Volatile private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    private val lastInterruptTime = AtomicLong(0L)
    private var onInterruptDetected: (() -> Unit)? = null

    fun setOnInterruptListener(listener: () -> Unit) {
        onInterruptDetected = listener
    }

    fun start(): Boolean {
        if (isRunning) return true

        if (!micController.acquireMic(MicController.OWNER_INTERRUPT)) {
            val holder = micController.getCurrentOwner()
            Log.w(TAG, "Cannot start: mic busy ($holder)")
            return false
        }

        isRunning = true
        captureThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            runCaptureLoop()
        }, "Jarvis-InterruptDetector").also {
            it.priority = Thread.MAX_PRIORITY
            it.start()
        }
        Log.i(TAG, "Interrupt detector started")
        return true
    }

    fun stop() {
        if (!isRunning && captureThread == null && audioRecord == null) {
            return
        }

        isRunning = false
        val thread = captureThread
        captureThread = null
        thread?.let {
            if (it.isAlive) {
                it.interrupt()
                it.join(300)
            }
        }
        releaseAudioRecord()
        if (micController.isOwnedBy(MicController.OWNER_INTERRUPT)) {
            micController.releaseMic(MicController.OWNER_INTERRUPT)
        }
        Log.i(TAG, "Interrupt detector stopped")
    }

    private fun runCaptureLoop() {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        val bufSize = (FRAME_SAMPLES * 2 * 2).coerceAtLeast(minBuf)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE, CHANNEL, ENCODING, bufSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord init failed")
                stop()
                return
            }

            audioRecord?.startRecording()
            val buffer = ShortArray(FRAME_SAMPLES)
            var speechFrames = 0

            while (isRunning) {
                val read = audioRecord?.read(buffer, 0, FRAME_SAMPLES) ?: -1
                if (read <= 0) {
                    if (read < 0) Thread.sleep(10)
                    continue
                }

                val rms = calculateRms(buffer, read)
                val now = System.currentTimeMillis()

                if (rms > ENERGY_THRESHOLD) {
                    speechFrames++
                    if (speechFrames >= SPEECH_CONFIRM_FRAMES &&
                        (now - lastInterruptTime.get()) > COOLDOWN_MS
                    ) {
                        lastInterruptTime.set(now)
                        speechFrames = 0
                        Log.i(TAG, "Interrupt detected (rms=$rms, frames=$speechFrames)")
                        mainHandler.post {
                            onInterruptDetected?.invoke()
                        }
                        break
                    }
                } else {
                    speechFrames = 0
                }

                if (speechFrames > MAX_SPEECH_FRAMES) {
                    speechFrames = 0
                }
            }
        } catch (_: InterruptedException) {
            // Normal shutdown
        } catch (e: Exception) {
            Log.e(TAG, "Capture loop error", e)
        } finally {
            releaseAudioRecord()
        }
    }

    private fun calculateRms(buffer: ShortArray, length: Int): Double {
        var sum = 0.0
        for (i in 0 until length) {
            sum += buffer[i].toDouble() * buffer[i].toDouble()
        }
        return Math.sqrt(sum / length)
    }

    private fun releaseAudioRecord() {
        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
        } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
    }
}
