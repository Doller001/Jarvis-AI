package com.jarvis.assistant.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.util.Log

enum class CaptureLifecycleState {
    IDLE,
    START,
    RUNNING,
    STOP_REQUESTED,
    STOPPING,
    STOPPED
}

/**
 * Low-latency audio capture worker utilizing Android AudioRecord with VOICE_RECOGNITION tuning.
 * Follows strict lifecycle management and coordinates with [MicController] to prevent mic conflicts.
 */
class LowLatencyAudioCapture(
    private val context: Context?,
    private val audioProcessor: NearFieldAudioProcessor = NearFieldAudioProcessor(sampleRate = 16000),
    private val micController: MicController = MicController(context)
) {
    companion object {
        private const val TAG = "LowLatencyAudioCapture"
        private const val OWNER_TAG = "LowLatencyAudioCapture"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val FRAME_SIZE_SAMPLES = 320 // 20ms chunk at 16kHz
    }

    @Volatile
    var lifecycleState: CaptureLifecycleState = CaptureLifecycleState.IDLE
        private set

    @Volatile
    private var isRecording = false
    private var recordingThread: Thread? = null
    private var audioRecord: AudioRecord? = null
    private val stateLock = Any()

    var onFrameProcessed: ((AudioProcessingResult) -> Unit)? = null
    var onEnvironmentChanged: ((EnvironmentProfile) -> Unit)? = null

    private var lastProfile: EnvironmentProfile = audioProcessor.profile

    fun start(): Boolean {
        synchronized(stateLock) {
            if (isRecording || lifecycleState == CaptureLifecycleState.RUNNING) {
                Log.d(TAG, "Audio capture is already running")
                return true
            }

            if (!micController.hasPermission()) {
                Log.w(TAG, "Cannot start audio capture — RECORD_AUDIO permission missing")
                return false
            }

            if (!micController.acquireMic(OWNER_TAG)) {
                Log.w(TAG, "Cannot start audio capture — mic is held by another component")
                return false
            }

            lifecycleState = CaptureLifecycleState.START
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
                        synchronized(stateLock) {
                            lifecycleState = CaptureLifecycleState.STOPPED
                            isRecording = false
                        }
                        micController.releaseMic(OWNER_TAG)
                        return@Thread
                    }

                    audioRecord?.startRecording()
                    synchronized(stateLock) {
                        lifecycleState = CaptureLifecycleState.RUNNING
                    }
                    Log.i(TAG, "Low-latency audio capture started (16kHz, VOICE_RECOGNITION mode)")

                    val buffer = ShortArray(FRAME_SIZE_SAMPLES)

                    while (isRecording && lifecycleState == CaptureLifecycleState.RUNNING) {
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
                    cleanupAudioRecord()
                }
            }, "Jarvis-AudioCaptureThread").apply {
                priority = Thread.MAX_PRIORITY
                start()
            }
            return true
        }
    }

    private fun cleanupAudioRecord() {
        try {
            audioRecord?.stop()
        } catch (_: Exception) {}
        try {
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        micController.releaseMic(OWNER_TAG)
        synchronized(stateLock) {
            lifecycleState = CaptureLifecycleState.STOPPED
            isRecording = false
        }
        Log.i(TAG, "Audio capture cleanly stopped & mic released")
    }

    /**
     * Synchronously stops audio capture and ensures AudioRecord and its thread are completely released.
     */
    fun stop() {
        val threadToJoin: Thread?
        synchronized(stateLock) {
            if (!isRecording && lifecycleState == CaptureLifecycleState.STOPPED) return
            lifecycleState = CaptureLifecycleState.STOP_REQUESTED
            isRecording = false
            lifecycleState = CaptureLifecycleState.STOPPING
            threadToJoin = recordingThread
            recordingThread = null
        }

        try {
            threadToJoin?.interrupt()
            threadToJoin?.join(1000)
        } catch (e: InterruptedException) {
            Log.w(TAG, "Interrupted while waiting for audio capture thread to terminate", e)
        }

        cleanupAudioRecord()
    }

    fun isCapturing(): Boolean = lifecycleState == CaptureLifecycleState.RUNNING

    fun getProcessor(): NearFieldAudioProcessor = audioProcessor
}
