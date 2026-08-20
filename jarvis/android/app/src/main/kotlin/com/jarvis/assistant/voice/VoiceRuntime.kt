package com.jarvis.assistant.voice

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Central voice coordinator. Owns the [VoiceStateMachine] and guarantees that
 * exactly one component owns the microphone at any time:
 *
 *   WAKE_LISTENING    -> WakeWordEngine (offline detector or fallback STT)
 *   WAKE_DETECTED     -> beep, then hand mic to command recognizer
 *   COMMAND_LISTENING -> SpeechRecognizer owns the mic (with command timeout)
 *   PROCESSING        -> command routed to the brain
 *   SPEAKING          -> TTS owns the mic path; wake detector paused so
 *                        Jarvis never triggers on its own voice
 *   WAKE_LISTENING    -> detector resumed after cooldown
 */
class VoiceRuntime(
    private val context: Context? = null,
    config: WakeWordConfig = WakeWordConfig(),
    private val wakeWordEngine: WakeWordEngine = WakeWordEngine(
        context = context,
        config = config,
        detector = context?.let { PorcupineWakeWordDetector(it, config) }
    ),
    private val speechRecognizer: SpeechRecognizer = SpeechRecognizer(context = context),
    private val ttsEngine: TextToSpeechEngine = TextToSpeechEngine(context = context)
) {
    companion object {
        private const val TAG = "VoiceRuntime"
    }

    private val config: WakeWordConfig = config
    private val stateMachine = VoiceStateMachine()
    val state: VoiceState get() = stateMachine.state

    private val mainHandler = Handler(Looper.getMainLooper())
    private var commandCallback: ((String) -> Unit)? = null
    private var stateListener: ((VoiceState) -> Unit)? = null
    private var tone: ToneGenerator? = null

    private val commandTimeoutRunnable = Runnable { onCommandTimeout() }

    fun setStateListener(listener: (VoiceState) -> Unit) {
        stateListener = listener
        listener(state)
    }

    fun startRuntime(onCommandRecognized: (String) -> Unit) {
        if (state != VoiceState.STOPPED) return
        commandCallback = onCommandRecognized
        stateMachine.transition(VoiceState.STARTING)
        notifyState()
        wakeWordEngine.startMonitoring(
            onWake = { fallbackText -> onWakeDetected(fallbackText) },
            onError = { e -> handleError("wake detector: ${e.message}") }
        )
        stateMachine.transition(VoiceState.WAKE_LISTENING)
        notifyState()
        Log.i(TAG, "Runtime started — waiting for wake word")
    }

    private fun onWakeDetected(fallbackText: String?) {
        Log.i(TAG, "Wake detected${if (fallbackText != null) " (fallback: '$fallbackText')" else ""}")
        if (!stateMachine.transition(VoiceState.WAKE_DETECTED)) {
            Log.w(TAG, "Wake ignored — state is ${stateMachine.state}")
            return
        }
        notifyState()

        playBeep()
        wakeWordEngine.pause() // mic handoff to command recognizer

        stateMachine.transition(VoiceState.COMMAND_LISTENING)
        notifyState()

        if (fallbackText != null) {
            // Fallback mode: the command is embedded in the recognized utterance.
            onCommandReceived(wakeWordEngine.extractCommand(fallbackText))
        } else {
            startCommandListening()
        }
    }

    private fun startCommandListening() {
        mainHandler.removeCallbacks(commandTimeoutRunnable)
        mainHandler.postDelayed(commandTimeoutRunnable, config.commandTimeoutMs)
        speechRecognizer.startListening(
            onResult = { cmd -> onCommandReceived(cmd) },
            onError = { error -> handleError("recognizer error $error") }
        )
        Log.i(TAG, "SpeechRecognizer started — command mode (timeout ${config.commandTimeoutMs}ms)")
    }

    private fun onCommandReceived(command: String) {
        mainHandler.removeCallbacks(commandTimeoutRunnable)
        speechRecognizer.destroy()
        if (command.isBlank()) {
            Log.i(TAG, "Empty command — returning to wake listening")
            scheduleWakeResume()
            return
        }
        if (!stateMachine.transition(VoiceState.PROCESSING)) {
            Log.w(TAG, "Command ignored — state is ${stateMachine.state}")
            return
        }
        notifyState()
        Log.i(TAG, "Processing command")
        commandCallback?.invoke(command)
    }

    private fun onCommandTimeout() {
        Log.w(TAG, "Command timeout — no speech detected")
        speechRecognizer.destroy()
        handleError("command timeout")
    }

    private fun handleError(reason: String) {
        Log.e(TAG, "Voice error: $reason — recovering")
        mainHandler.removeCallbacks(commandTimeoutRunnable)
        speechRecognizer.destroy()
        if (stateMachine.transition(VoiceState.ERROR)) notifyState()
        mainHandler.postDelayed({
            if (state == VoiceState.STOPPED) return@postDelayed
            wakeWordEngine.resume()
            if (stateMachine.recoverFromError()) notifyState()
            Log.i(TAG, "Recovered — wake listening")
        }, config.cooldownMs)
    }

    /** Blank command path: cooldown, then hand the mic back to the detector. */
    private fun scheduleWakeResume() {
        mainHandler.postDelayed({
            if (state == VoiceState.STOPPED) return@postDelayed
            wakeWordEngine.resume()
            if (stateMachine.transition(VoiceState.WAKE_LISTENING)) notifyState()
        }, config.cooldownMs)
    }

    /** Pauses wake detection while Jarvis speaks; resumes after TTS + cooldown. */
    fun speakResponse(text: String, onComplete: () -> Unit = {}) {
        if (!stateMachine.transition(VoiceState.SPEAKING)) {
            Log.w(TAG, "TTS skipped — state is ${stateMachine.state}")
            onComplete()
            return
        }
        notifyState()
        wakeWordEngine.pause()
        Log.i(TAG, "TTS started — wake detector paused")
        ttsEngine.speak(text) {
            Log.i(TAG, "TTS completed")
            mainHandler.postDelayed({
                if (state == VoiceState.STOPPED) {
                    onComplete()
                    return@postDelayed
                }
                wakeWordEngine.resume()
                if (stateMachine.transition(VoiceState.WAKE_LISTENING)) notifyState()
                onComplete()
            }, config.ttsCooldownMs)
        }
    }

    fun toggleMonitoring(): Boolean {
        return if (state == VoiceState.STOPPED) {
            startRuntime(commandCallback ?: {})
            true
        } else {
            stopRuntime()
            false
        }
    }

    fun stopRuntime() {
        mainHandler.removeCallbacks(commandTimeoutRunnable)
        speechRecognizer.destroy()
        wakeWordEngine.stopMonitoring()
        if (stateMachine.transition(VoiceState.STOPPED)) notifyState()
        Log.i(TAG, "Runtime stopped")
    }

    fun release() {
        mainHandler.removeCallbacks(commandTimeoutRunnable)
        speechRecognizer.destroy()
        wakeWordEngine.release()
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