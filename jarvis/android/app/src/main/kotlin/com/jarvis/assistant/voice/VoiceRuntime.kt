package com.jarvis.assistant.voice

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.Log

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
    private val audioCapture: LowLatencyAudioCapture = LowLatencyAudioCapture(context, audioProcessor, micController)
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

        playBeep()

        if (!stateMachine.transition(VoiceState.LISTENING)) {
            Log.w(TAG, "Cannot transition to LISTENING from $state")
            return
        }
        notifyState()

        startRecognition()
    }

    private fun startRecognition() {
        mainHandler.removeCallbacks(commandTimeoutRunnable)
        mainHandler.postDelayed(commandTimeoutRunnable, COMMAND_TIMEOUT_MS)

        speechController.startListening(
            onResult = { utterance -> onCommandReceived(utterance) },
            onError = { errorCode, errorMessage ->
                Log.w(TAG, "Speech recognition error ($errorCode): $errorMessage")
                handleError(errorMessage)
            },
            onRmsChanged = { rms -> onRmsChanged?.invoke(rms) }
        )
    }

    private fun onCommandReceived(command: String) {
        mainHandler.removeCallbacks(commandTimeoutRunnable)
        speechController.destroy()

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
        handleError("Speech timeout")
    }

    private fun handleError(reason: String) {
        Log.w(TAG, "Voice error occurred: $reason")
        VoiceDiagnostics.logError(android.speech.SpeechRecognizer.ERROR_CLIENT)
        mainHandler.removeCallbacks(commandTimeoutRunnable)
        speechController.destroy()

        if (stateMachine.transition(VoiceState.ERROR)) notifyState()

        mainHandler.postDelayed({
            if (stateMachine.recoverFromError()) notifyState()
        }, ERROR_RECOVERY_MS)
    }

    /**
     * Speaks assistant response via TTS and returns state to IDLE upon completion.
     */
    fun speakResponse(text: String, onComplete: () -> Unit = {}) {
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
                onComplete()
            }, 200L)
        }
    }

    fun setSpeechRate(rate: Float) {
        ttsEngine.setSpeechRate(rate)
    }

    fun stopRuntime() {
        mainHandler.removeCallbacksAndMessages(null)
        audioCapture.stop()
        speechController.destroy()
        ttsEngine.stop()
        if (stateMachine.transition(VoiceState.IDLE)) notifyState()
        Log.i(TAG, "VoiceRuntime stopped — IDLE")
    }

    fun release() {
        mainHandler.removeCallbacksAndMessages(null)
        audioCapture.stop()
        speechController.destroy()
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
            if (tone == null) {
                tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
            }
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (e: Exception) {
            Log.w(TAG, "Beep tone generation failed", e)
        }
    }

    private fun notifyState() {
        stateListener?.invoke(state)
    }

    fun getMicController(): MicController = micController
}