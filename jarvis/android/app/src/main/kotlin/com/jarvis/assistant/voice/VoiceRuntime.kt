package com.jarvis.assistant.voice

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Central voice coordinator for push-to-talk / direct command listening:
 *
 *   STOPPED / IDLE    -> Waiting for user to tap mic or trigger command
 *   COMMAND_LISTENING -> SpeechRecognizer owns the mic (with command timeout)
 *   PROCESSING        -> Command routed to local brain or cloud brain
 *   SPEAKING          -> TTS speaks response to user
 *   STOPPED           -> Returned to resting state
 */
class VoiceRuntime(
    private val context: Context? = null,
    private val speechRecognizer: SpeechRecognizer = SpeechRecognizer(context = context),
    private val ttsEngine: TextToSpeechEngine = TextToSpeechEngine(context = context),
    private val audioRouteManager: AudioRouteManager = AudioRouteManager(context = context),
    private val audioProcessor: NearFieldAudioProcessor = NearFieldAudioProcessor(sampleRate = 16000),
    private val audioCapture: LowLatencyAudioCapture = LowLatencyAudioCapture(context, audioProcessor)
) {
    companion object {
        private const val TAG = "VoiceRuntime"
        private const val COMMAND_TIMEOUT_MS = 8000L
        private const val COOLDOWN_MS = 500L
    }

    private val stateMachine = VoiceStateMachine()
    val state: VoiceState get() = stateMachine.state

    private val mainHandler = Handler(Looper.getMainLooper())
    private var commandCallback: ((String) -> Unit)? = null
    private var stateListener: ((VoiceState) -> Unit)? = null
    private var tone: ToneGenerator? = null

    var onEnvironmentChanged: ((EnvironmentProfile) -> Unit)? = null
    var onAudioMetrics: ((AudioProcessingResult) -> Unit)? = null

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
        Log.i(TAG, "Runtime initialized — ready for voice commands")
    }

    /** Starts listening for a voice command immediately. */
    fun startListeningForCommand() {
        if (state == VoiceState.SPEAKING) {
            ttsEngine.stop()
        }
        playBeep()
        if (!stateMachine.transition(VoiceState.COMMAND_LISTENING)) {
            Log.w(TAG, "Cannot transition to COMMAND_LISTENING from $state")
            return
        }
        notifyState()
        startCommandListening()
    }

    private fun startCommandListening() {
        mainHandler.removeCallbacks(commandTimeoutRunnable)
        mainHandler.postDelayed(commandTimeoutRunnable, COMMAND_TIMEOUT_MS)
        speechRecognizer.startListening(
            onResult = { cmd -> onCommandReceived(cmd) },
            onError = { error -> handleError("recognizer error $error") }
        )
        Log.i(TAG, "SpeechRecognizer started — command mode (timeout ${COMMAND_TIMEOUT_MS}ms)")
    }

    private fun onCommandReceived(command: String) {
        mainHandler.removeCallbacks(commandTimeoutRunnable)
        speechRecognizer.destroy()
        if (command.isBlank()) {
            Log.i(TAG, "Empty command — returning to idle")
            if (stateMachine.transition(VoiceState.STOPPED)) notifyState()
            return
        }
        if (!stateMachine.transition(VoiceState.PROCESSING)) {
            Log.w(TAG, "Command ignored — state is ${stateMachine.state}")
            return
        }
        notifyState()
        Log.i(TAG, "Processing command: '$command'")
        commandCallback?.invoke(command)
    }

    private fun onCommandTimeout() {
        Log.w(TAG, "Command timeout — no speech detected")
        speechRecognizer.destroy()
        handleError("command timeout")
    }

    private fun handleError(reason: String) {
        Log.w(TAG, "Voice event: $reason — returning to idle")
        mainHandler.removeCallbacks(commandTimeoutRunnable)
        speechRecognizer.destroy()
        if (stateMachine.transition(VoiceState.ERROR)) notifyState()
        mainHandler.postDelayed({
            if (stateMachine.recoverFromError()) notifyState()
        }, COOLDOWN_MS)
    }

    /** Speaks response using TTS engine and returns to STOPPED state upon completion. */
    fun speakResponse(text: String, onComplete: () -> Unit = {}) {
        if (!stateMachine.transition(VoiceState.SPEAKING)) {
            Log.w(TAG, "TTS skipped — state is ${stateMachine.state}")
            onComplete()
            return
        }
        notifyState()
        Log.i(TAG, "TTS started")
        ttsEngine.speak(text) {
            Log.i(TAG, "TTS completed")
            mainHandler.postDelayed({
                if (stateMachine.transition(VoiceState.STOPPED)) notifyState()
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
        speechRecognizer.destroy()
        ttsEngine.stop()
        if (stateMachine.transition(VoiceState.STOPPED)) notifyState()
        Log.i(TAG, "Runtime stopped")
    }

    fun release() {
        mainHandler.removeCallbacksAndMessages(null)
        audioCapture.stop()
        speechRecognizer.destroy()
        audioRouteManager.release()
        ttsEngine.shutdown()
        try {
            tone?.release()
        } catch (_: Exception) {
        }
        tone = null
        stateListener = null
        stateMachine.transition(VoiceState.STOPPED)
        Log.i(TAG, "Runtime released")
    }

    private fun playBeep() {
        try {
            if (tone == null) {
                tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
            }
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (e: Exception) {
            Log.w(TAG, "Beep failed", e)
        }
    }

    private fun notifyState() {
        stateListener?.invoke(state)
    }
}