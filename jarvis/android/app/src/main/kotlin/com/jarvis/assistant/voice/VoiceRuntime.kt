package com.jarvis.assistant.voice

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.jarvis.assistant.voice.wakeword.LiveKitWakeWordEngine
import com.jarvis.assistant.voice.wakeword.WakeWordConfig

/**
 * Central voice coordinator for Jarvis (Phase 1 & 2 Clean Architecture).
 *
 * Enforces Single Mic Owner architecture:
 *   IDLE        -> Waiting for user action / push-to-talk
 *   LISTENING   -> SpeechController owns the microphone exclusively
 *   PROCESSING  -> Utterance routed to subconscious/conscious brain
 *   SPEAKING    -> TTS plays response
 *   IDLE        -> Return to idle resting state
 */
class VoiceRuntime(
    private val context: Context? = null,
    private val micController: MicController = MicController(context),
    private val speechController: SpeechController = SpeechController(context, micController),
    private val ttsEngine: TextToSpeechEngine = TextToSpeechEngine(context = context),
    private val audioRouteManager: AudioRouteManager = AudioRouteManager(context = context),
    private val audioProcessor: NearFieldAudioProcessor = NearFieldAudioProcessor(sampleRate = 16000),
    private val audioCapture: LowLatencyAudioCapture = LowLatencyAudioCapture(context, audioProcessor, micController),
    private val wakeEngine: LiveKitWakeWordEngine = LiveKitWakeWordEngine(context, WakeWordConfig())
) {
    companion object {
        private const val TAG = "VoiceRuntime"
        private const val COMMAND_TIMEOUT_MS = 8000L
        private const val ERROR_RECOVERY_MS = 500L
    }

    private val stateMachine = VoiceStateMachine()
    val state: VoiceState get() = stateMachine.state

    private val mainHandler = Handler(Looper.getMainLooper())
    private var commandCallback: ((String) -> Unit)? = null
    private var stateListener: ((VoiceState) -> Unit)? = null
    private var tone: ToneGenerator? = null

    var onEnvironmentChanged: ((EnvironmentProfile) -> Unit)? = null
    var onAudioMetrics: ((AudioProcessingResult) -> Unit)? = null
    var onRmsChanged: ((Float) -> Unit)? = null

    private val commandTimeoutRunnable = Runnable { onCommandTimeout() }

    fun setStateListener(listener: (VoiceState) -> Unit) {
        stateListener = listener
        listener(state)
    }

    fun startRuntime(onCommandRecognized: (String) -> Unit) {
        commandCallback = onCommandRecognized
        audioRouteManager.start()
        audioCapture.onEnvironmentChanged = { env -> onEnvironmentChanged?.invoke(env) }
        audioCapture.onFrameProcessed = { res -> onAudioMetrics?.invoke(res) }
        VoiceDiagnostics.logMicState("VoiceRuntime initialized in IDLE state")

        // Start always-listening wake-word detection (offline). If the ONNX
        // models are missing, the engine logs a warning and does nothing —
        // push-to-talk still works via startListeningForCommand().
        startWakeMonitoring()
    }

    private fun startWakeMonitoring() {
        wakeEngine.setOnWakeListener { _ ->
            Log.i(TAG, "Wake word event -> entering command listening")
            if (stateMachine.transition(VoiceState.WAKE)) notifyState()
            // Hand the mic to the command recognizer.
            wakeEngine.pause()
            startListeningForCommand()
        }
        wakeEngine.setOnErrorListener { error ->
            Log.w(TAG, "Wake-word engine unavailable: ${error.message}")
        }
        wakeEngine.startMonitoring()
    }

    /**
     * Maps a sensitivity label to a 0..1 value and applies it live to the engine.
     * "Low" = 0.5f, "Balanced" = 0.8f, "High" = 1.0f.
     */
    fun setWakeSensitivity(label: String) {
        val value = when (label.lowercase()) {
            "low" -> 0.5f
            "high" -> 1.0f
            else -> 0.8f
        }
        wakeEngine.setSensitivity(value)
    }
    fun toggleMonitoring(): Boolean {
        if (wakeEngine.isMonitoringNow) {
            wakeEngine.stopMonitoring()
            if (stateMachine.state == VoiceState.WAKE) {
                if (stateMachine.transition(VoiceState.IDLE)) notifyState()
            }
            Log.i(TAG, "Wake-word monitoring toggled OFF")
            return false
        }
        if (!wakeEngine.isAvailable) {
            Log.w(TAG, "Cannot start wake-word monitoring — ONNX models unavailable")
            return false
        }
        startWakeMonitoring()
        Log.i(TAG, "Wake-word monitoring toggled ON")
        return true
    }

    /**
     * Starts listening for user command.
     * Enforces single mic owner: stops any background audio recording before activating SpeechController.
     */
    fun startListeningForCommand() {
        if (state == VoiceState.SPEAKING) {
            ttsEngine.stop()
        }

        // 1. Ensure background audio capture is completely stopped and released
        if (audioCapture.isCapturing()) {
            Log.d(TAG, "Stopping background audio capture before starting speech recognition")
            audioCapture.stop()
        }

        if (!stateMachine.transition(VoiceState.LISTENING)) {
            Log.w(TAG, "Cannot transition to LISTENING from $state")
            return
        }
        notifyState()

        // Activate voice routing only for the duration of active listening
        audioRouteManager.activateVoiceRouting()

        playBeep()

        // Wait 160ms for beep to finish before acquiring mic to avoid speaker->mic feedback loop
        mainHandler.postDelayed({
            if (state == VoiceState.LISTENING) {
                startRecognition()
            }
        }, 160L)
    }

    private fun startRecognition() {
        mainHandler.removeCallbacks(commandTimeoutRunnable)
        mainHandler.postDelayed(commandTimeoutRunnable, COMMAND_TIMEOUT_MS)

        speechController.startListening(
            onResult = { utterance -> onCommandReceived(utterance) },
            onError = { errorCode, errorMessage ->
                Log.w(TAG, "Speech recognition error ($errorCode): $errorMessage")
                handleError(errorCode, errorMessage)
            },
            onRmsChanged = { rms ->
                if (rms > 2f) {
                    // Reset command timeout when active speech / sound is being captured
                    mainHandler.removeCallbacks(commandTimeoutRunnable)
                    mainHandler.postDelayed(commandTimeoutRunnable, COMMAND_TIMEOUT_MS)
                }
                onRmsChanged?.invoke(rms)
            }
        )
    }

    private fun onCommandReceived(command: String) {
        mainHandler.removeCallbacks(commandTimeoutRunnable)
        speechController.destroy()
        audioRouteManager.deactivateVoiceRouting()

        if (command.isBlank()) {
            Log.i(TAG, "Empty utterance captured — returning to IDLE")
            if (stateMachine.transition(VoiceState.IDLE)) notifyState()
            return
        }

        if (!stateMachine.transition(VoiceState.PROCESSING)) {
            Log.w(TAG, "Command received but cannot transition to PROCESSING from $state")
            return
        }
        notifyState()
        VoiceDiagnostics.logResult("Passing command to brain router: '$command'")
        commandCallback?.invoke(command)
    }

    private fun onCommandTimeout() {
        Log.w(TAG, "Voice command timeout — no speech detected within ${COMMAND_TIMEOUT_MS}ms")
        speechController.destroy()
        audioRouteManager.deactivateVoiceRouting()
        handleError(android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT, "Speech timeout")
    }

    private fun handleError(errorCode: Int = android.speech.SpeechRecognizer.ERROR_CLIENT, reason: String) {
        Log.w(TAG, "Voice error occurred ($errorCode): $reason")
        VoiceDiagnostics.logError(errorCode)
        mainHandler.removeCallbacks(commandTimeoutRunnable)
        speechController.destroy()
        audioRouteManager.deactivateVoiceRouting()

        if (stateMachine.transition(VoiceState.ERROR)) notifyState()

        mainHandler.postDelayed({
            if (stateMachine.recoverFromError()) {
                notifyState()
                resumeWakeAfterCommand()
            }
        }, ERROR_RECOVERY_MS)
    }

    /**
     * Speaks assistant response via TTS and returns state to IDLE upon completion.
     */
    fun speakResponse(text: String, onComplete: () -> Unit = {}) {
        audioRouteManager.deactivateVoiceRouting()
        if (text.isBlank()) {
            if (stateMachine.transition(VoiceState.IDLE)) notifyState()
            onComplete()
            return
        }

        if (!stateMachine.transition(VoiceState.SPEAKING)) {
            Log.w(TAG, "TTS requested but cannot transition to SPEAKING from $state")
            onComplete()
            return
        }
        notifyState()

        ttsEngine.speak(text) {
            Log.i(TAG, "TTS completed speaking — returning to IDLE")
            mainHandler.postDelayed({
                if (stateMachine.transition(VoiceState.IDLE)) notifyState()
                audioRouteManager.ensureNormalAudioMode()
                // Return the microphone to the wake-word detector.
                resumeWakeAfterCommand()
                onComplete()
            }, 200L)
        }
    }

    fun setSpeechRate(rate: Float) {
        ttsEngine.setSpeechRate(rate)
    }

    /**
     * After a command cycle finishes (TTS done / error / timeout) hand the mic
     * back to the wake-word detector if it was active.
     */
    private fun resumeWakeAfterCommand() {
        if (wakeEngine.isMonitoringNow) {
            wakeEngine.resume()
        }
    }

    fun stopRuntime() {
        mainHandler.removeCallbacksAndMessages(null)
        audioCapture.stop()
        speechController.destroy()
        wakeEngine.stopMonitoring()
        audioRouteManager.deactivateVoiceRouting()
        ttsEngine.stop()
        if (stateMachine.transition(VoiceState.IDLE)) notifyState()
        Log.i(TAG, "VoiceRuntime stopped — IDLE")
    }

    fun release() {
        mainHandler.removeCallbacksAndMessages(null)
        audioCapture.stop()
        speechController.destroy()
        wakeEngine.release()
        audioRouteManager.release()
        ttsEngine.shutdown()
        try {
            tone?.release()
        } catch (_: Exception) {}
        tone = null
        stateListener = null
        stateMachine.transition(VoiceState.IDLE)
        Log.i(TAG, "VoiceRuntime released")
    }

    private fun playBeep() {
        try {
            val beepTone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60)
            beepTone.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            mainHandler.postDelayed({
                try {
                    beepTone.release()
                } catch (_: Exception) {}
            }, 250L)
        } catch (e: Exception) {
            Log.w(TAG, "Beep tone generation failed", e)
        }
    }

    private fun notifyState() {
        stateListener?.invoke(state)
    }

    fun getMicController(): MicController = micController
}