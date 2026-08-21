package com.jarvis.assistant.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.util.Log

/**
 * Low-latency audio capture worker utilizing Android AudioRecord with VOICE_RECOGNITION tuning.
 * Feeds frames into NearFieldAudioProcessor and notifies listeners in real-time.
 */
class LowLatencyAudioCapture(
    private val context: Context?,
    private val audioProcessor: NearFieldAudioProcessor = NearFieldAudioProcessor(sampleRate = 16000)
) {
    companion object {
        private const val TAG = "LowLatencyAudioCapture"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val FRAME_SIZE_SAMPLES = 320 // 20ms chunk at 16kHz
    }

    @Volatile
    private var isRecording = false
    private var recordingThread: Thread? = null
    private var audioRecord: AudioRecord? = null

    var onFrameProcessed: ((AudioProcessingResult) -> Unit)? = null
    var onEnvironmentChanged: ((EnvironmentProfile) -> Unit)? = null

    private var lastProfile: EnvironmentProfile = audioProcessor.profile

    fun start() {
        if (isRecording) return
        isRecording = true

        recordingThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufferSize = (FRAME_SIZE_SAMPLES * 2 * 4).coerceAtLeast(minBufSize)

            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord initialization failed!")
                    isRecording = false
                    return@Thread
                }

                audioRecord?.startRecording()
                Log.i(TAG, "Low-latency audio capture started (16kHz, VOICE_RECOGNITION mode)")

                val buffer = ShortArray(FRAME_SIZE_SAMPLES)

                while (isRecording) {
                    val readCount = audioRecord?.read(buffer, 0, FRAME_SIZE_SAMPLES) ?: -1
                    if (readCount > 0) {
                        val activeSlice = if (readCount == FRAME_SIZE_SAMPLES) buffer else buffer.copyOf(readCount)
                        val result = audioProcessor.processFrame(activeSlice)

                        if (result.environment != lastProfile) {
                            lastProfile = result.environment
                            onEnvironmentChanged?.invoke(result.environment)
                        }

                        onFrameProcessed?.invoke(result)
                    } else if (readCount < 0) {
                        Log.w(TAG, "AudioRecord read error: $readCount")
                        Thread.sleep(10)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in audio capture loop", e)
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (_: Exception) {}
                audioRecord = null
                Log.i(TAG, "Audio capture stopped")
            }
        }, "Jarvis-AudioCaptureThread").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        isRecording = false
        recordingThread?.interrupt()
        recordingThread = null
    }

    fun getProcessor(): NearFieldAudioProcessor = audioProcessor
}
