package com.jarvis.assistant.voice

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.jarvis.assistant.voice.wakeword.LiveKitWakeWordEngine
import com.jarvis.assistant.voice.wakeword.WakeWordConfig
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Central voice coordinator with session generation, interrupt support, and optimized transitions.
 *
 * STATE MACHINE (canonical):
 *   DISABLED → WAKE_LISTENING → ACKNOWLEDGING → COMMAND_LISTENING
 *            → PROCESSING → SPEAKING → WAKE_LISTENING
 *
 * INTERRUPT PATH:
 *   SPEAKING → INTERRUPTING → COMMAND_LISTENING
 *   PROCESSING → INTERRUPTING → COMMAND_LISTENING
 *
 * SESSION GENERATION:
 *   Every new session (wake detect, STT start, interrupt) increments generation.
 *   All callbacks check generation before invoking, preventing stale actions.
 *
 * PERFORMANCE:
 *   - No artificial delays (80ms + 50ms removed)
 *   - Atomic mic handoff between wake/stt/interrupt
 *   - TTS barge-in with acknowledgement
 */
class VoiceRuntime(
    private val context: Context? = null,
    private val micController: MicController = MicController(context),
    private val speechController: SpeechController = SpeechController(context, micController),
    private val ttsEngine: TextToSpeechEngine = TextToSpeechEngine(context = context),
    private val audioRouteManager: AudioRouteManager = AudioRouteManager(context = context),
    private val audioSessionManager: AudioSessionManager = AudioSessionManager(context, audioRouteManager),
    private val audioProcessor: NearFieldAudioProcessor = NearFieldAudioProcessor(sampleRate = 16000),
    private val audioCapture: LowLatencyAudioCapture = LowLatencyAudioCapture(context, audioProcessor, micController),
    private val wakeEngine: LiveKitWakeWordEngine = LiveKitWakeWordEngine(
        context = context,
        config = WakeWordConfig(),
        micController = micController
    )
) {
    companion object {
        private const val TAG = "VoiceRuntime"
        private const val COMMAND_TIMEOUT_MS = 8000L
        private const val PROCESSING_TIMEOUT_MS = 6000L
        private const val ERROR_RECOVERY_MS = 500L
        private const val WAKE_COOLDOWN_MS = 2500L
    }

    private val stateMachine = VoiceStateMachine(VoiceState.DISABLED)
    val state: VoiceState get() = stateMachine.state

    private val mainHandler = Handler(Looper.getMainLooper())
    private var commandCallback: ((String) -> Unit)? = null
    private var stateListener: ((VoiceState) -> Unit)? = null
    private var tone: ToneGenerator? = null

    // Session generation — invalidates stale callbacks
    private val sessionGeneration = AtomicLong(0L)

    // Wake session lock — prevents duplicate wake callbacks
    private val wakeSessionActive = AtomicBoolean(false)
    @Volatile private var lastWakeAcceptedAtMs = 0L

    // Single authority flag — reflects SettingsManager.wakeWordEnabled
    @Volatile private var wakeEnabled = false

    // Interrupt detector — lightweight speech detection during TTS
    private val interruptDetector = InterruptDetector(micController)

    var onEnvironmentChanged: ((EnvironmentProfile) -> Unit)? = null
    var onAudioMetrics: ((AudioProcessingResult) -> Unit)? = null
    var onRmsChanged: ((Float) -> Unit)? = null

    private val commandTimeoutRunnable = Runnable { onCommandTimeout() }
    private val processingTimeoutRunnable = Runnable { onProcessingTimeout() }

    fun setStateListener(listener: (VoiceState) -> Unit) {
        stateListener = listener
        listener(state)
    }

    /**
     * Initialises all components. Does NOT start wake detection.
     * Call setWakeEnabled(true) after startup.
     */
    fun startRuntime(onCommandRecognized: (String) -> Unit) {
        commandCallback = onCommandRecognized
        audioRouteManager.start()
        audioCapture.onEnvironmentChanged = { env -> onEnvironmentChanged?.invoke(env) }
        audioCapture.onFrameProcessed = { res -> onAudioMetrics?.invoke(res) }

        // Set up interrupt detector callback
        interruptDetector.setOnInterruptListener {
            handleInterruptDetected()
        }

        VoiceDiagnostics.logMicState("VoiceRuntime initialised — DISABLED (wake NOT started)")
        Log.i(TAG, "VoiceRuntime started. Call setWakeEnabled(true) to begin wake monitoring.")

        // Set up wake engine error handler once.
        wakeEngine.setOnErrorListener { error ->
            Log.w(TAG, "Wake-word engine error: ${error.message}")
            handleVoiceEngineFailure("WakeEngine", error)
        }
    }

    /**
     * Single authority entry point for enabling / disabling wake detection.
     */
    fun setWakeEnabled(enabled: Boolean) {
        wakeEnabled = enabled
        if (enabled) {
            activateWakeMode()
        } else {
            deactivateWakeMode()
        }
    }

    private fun activateWakeMode() {
        if (stateMachine.isWakeListening) return
        if (!installWakeCallback()) return
        val started = wakeEngine.startMonitoring()
        if (started) {
            if (stateMachine.transition(VoiceState.WAKE_LISTENING)) notifyState()
            Log.i(TAG, "Wake mode ACTIVATED")
        } else {
            Log.w(TAG, "Wake engine failed to start — staying in current state")
        }
    }

    private fun deactivateWakeMode() {
        wakeEngine.stopMonitoring()
        interruptDetector.stop()
        speechController.destroy()
        micController.releaseAny()
        if (stateMachine.state != VoiceState.DISABLED) {
            stateMachine.recoverTo(VoiceState.DISABLED)
        }
        notifyState()
        Log.i(TAG, "Wake mode DEACTIVATED — mic released")
    }

    private var wakeCallbackInstalled = false
    private fun installWakeCallback(): Boolean {
        if (wakeCallbackInstalled) return true
        wakeCallbackInstalled = true
        wakeEngine.setOnWakeListener { _ ->
            handleWakeEvent()
        }
        return true
    }

    /**
     * Wake event gate — 7-step check before action.
     */
    private fun handleWakeEvent() {
        val now = SystemClock.elapsedRealtime()

        // Gate 1: Wake globally disabled.
        if (!wakeEnabled) {
            Log.d(TAG, "[WAKE_GATE] REJECT — wake disabled")
            return
        }

        // Gate 2: Not in WAKE_LISTENING state.
        if (!stateMachine.isWakeListening) {
            Log.d(TAG, "[WAKE_GATE] REJECT — state=${stateMachine.state}")
            return
        }

        // Gate 3: Mic owner must be WAKE or null.
        val micOwner = micController.getCurrentOwner()
        if (micOwner != null && micOwner != MicController.OWNER_WAKE) {
            Log.d(TAG, "[WAKE_GATE] REJECT — mic owned by '$micOwner'")
            return
        }

        // Gate 4: Cooldown.
        if (now - lastWakeAcceptedAtMs < WAKE_COOLDOWN_MS) {
            Log.d(TAG, "[WAKE_GATE] REJECT — cooldown")
            return
        }

        // Gate 5+6+7: AtomicBoolean session lock.
        if (!wakeSessionActive.compareAndSet(false, true)) {
            Log.d(TAG, "[WAKE_GATE] REJECT — session active")
            return
        }

        // All gates passed.
        lastWakeAcceptedAtMs = now
        Log.i(TAG, "[WAKE_GATE] ACCEPT — starting acknowledgement")
        startAcknowledgement()
    }

    /**
     * Optimized acknowledgement sequence — no artificial delays.
     *
     * 1. Invalidate wake session (increment generation)
     * 2. Pause wake engine (non-blocking)
     * 3. Start STT immediately (no 80ms delay)
     */
    /**
     * Optimized acknowledgement sequence — no artificial delays.
     *
     * 1. Invalidate wake session (increment generation)
     * 2. Pause wake engine (synchronous release)
     * 3. Start STT immediately
     */
    private fun startAcknowledgement() {
        if (!stateMachine.transition(VoiceState.ACKNOWLEDGING)) {
            Log.w(TAG, "Cannot enter ACKNOWLEDGING from ${stateMachine.state}")
            wakeSessionActive.set(false)
            return
        }
        notifyState()

        // Invalidate wake session and increment generation
        val generation = sessionGeneration.incrementAndGet()
        VoiceDiagnostics.logSessionGeneration(generation)

        // Synchronously release wake engine audio capture
        wakeEngine.pause()

        // Start STT with confirmed wake word trigger
        startListeningForCommand(TriggerReason.WakeWordConfirmed)
    }

    fun setWakeSensitivity(label: String) {
        val value = when (label.lowercase()) {
            "low" -> 0.5f
            "high" -> 1.0f
            else -> 0.8f
        }
        wakeEngine.setSensitivity(value)
    }

    fun toggleMonitoring(): Boolean {
        val nowEnabled = !wakeEnabled
        setWakeEnabled(nowEnabled)
        return nowEnabled
    }

    /**
     * Explicit manual command entry point (e.g. mic button in UI or overlay).
     * Works seamlessly whether wake-word is enabled or disabled.
     */
    fun startManualCommand() {
        Log.i(TAG, "Explicit manual command trigger received from state ${stateMachine.state}")
        startListeningForCommand(TriggerReason.ManualButton)
    }

    /**
     * Starts command STT with strict TriggerReason verification.
     */
    fun startListeningForCommand(reason: TriggerReason = TriggerReason.ManualButton) {
        val generation = sessionGeneration.incrementAndGet()

        // Stop TTS if speaking
        if (stateMachine.isSpeaking) {
            ttsEngine.stop()
        }

        // Stop interrupt detector if running
        interruptDetector.stop()

        // Ensure background audio capture is stopped
        if (audioCapture.isCapturing()) audioCapture.stop()

        // Ensure wake engine mic is released
        wakeEngine.pause()

        if (!stateMachine.transition(VoiceState.COMMAND_LISTENING)) {
            Log.w(TAG, "Cannot enter COMMAND_LISTENING from ${stateMachine.state} — recovering")
            stateMachine.recoverTo(VoiceState.COMMAND_LISTENING)
        }
        notifyState()

        audioSessionManager.beginSession()

        // Start recognition with verified trigger reason
        startRecognition(reason, generation)
    }

    private fun startRecognition(reason: TriggerReason, generation: Long) {
        mainHandler.removeCallbacks(commandTimeoutRunnable)
        mainHandler.postDelayed(commandTimeoutRunnable, COMMAND_TIMEOUT_MS)

        val request = CommandListeningRequest(reason = reason, sessionId = generation)
        speechController.startListening(
            request = request,
            onResult = { utterance -> onCommandReceived(utterance, generation) },
            onError = { errorCode, errorMessage ->
                Log.w(TAG, "STT error ($errorCode): $errorMessage")
                handleError(errorCode, errorMessage)
            },
            onRmsChanged = { rms -> onRmsChanged?.invoke(rms) }
        )
    }

    private fun onCommandReceived(command: String, generation: Long) {
        mainHandler.removeCallbacks(commandTimeoutRunnable)
        speechController.destroy()
        audioSessionManager.endSession()

        // Stale callback guard
        if (generation != sessionGeneration.get()) {
            Log.d(TAG, "Stale STT result ignored (gen=$generation, current=${sessionGeneration.get()})")
            return
        }

        if (command.isBlank()) {
            Log.i(TAG, "Empty utterance — returning to wake mode")
            resumeWakeAfterCommand()
            return
        }

        if (!stateMachine.transition(VoiceState.PROCESSING)) {
            Log.w(TAG, "Cannot transition to PROCESSING from ${stateMachine.state}")
            resumeWakeAfterCommand()
            return
        }
        notifyState()
        VoiceDiagnostics.logResult("Command: '$command'")

        mainHandler.removeCallbacks(processingTimeoutRunnable)
        mainHandler.postDelayed(processingTimeoutRunnable, PROCESSING_TIMEOUT_MS)

        commandCallback?.invoke(command)
    }

    private fun onProcessingTimeout() {
        Log.w(TAG, "[PROCESSING_TIMEOUT] > ${PROCESSING_TIMEOUT_MS}ms — resetting session")
        if (stateMachine.state == VoiceState.PROCESSING) {
            handleError(android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT, "Processing timeout")
        }
    }

    private fun onCommandTimeout() {
        Log.w(TAG, "Command timeout after ${COMMAND_TIMEOUT_MS}ms")
        speechController.destroy()
        audioSessionManager.endSession()
        handleError(android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT, "Speech timeout")
    }

    private fun handleError(
        errorCode: Int = android.speech.SpeechRecognizer.ERROR_CLIENT,
        reason: String
    ) {
        Log.w(TAG, "Voice error ($errorCode): $reason")
        VoiceDiagnostics.logError(errorCode)
        mainHandler.removeCallbacks(commandTimeoutRunnable)
        mainHandler.removeCallbacks(processingTimeoutRunnable)
        speechController.destroy()
        audioSessionManager.endSession()
        interruptDetector.stop()

        if (stateMachine.transition(VoiceState.RECOVERING)) notifyState()

        mainHandler.postDelayed({
            stateMachine.recoverTo(VoiceState.WAKE_LISTENING)
            notifyState()
            resumeWakeAfterCommand()
        }, ERROR_RECOVERY_MS)
    }

    private fun handleVoiceEngineFailure(component: String, error: Throwable) {
        Log.e(TAG, "[$component] failure: ${error.message}")
        mainHandler.post {
            try { audioCapture.stop() } catch (_: Exception) {}
            try { interruptDetector.stop() } catch (_: Exception) {}
            try { speechController.destroy() } catch (_: Exception) {}
            try { audioRouteManager.deactivateVoiceRouting() } catch (_: Exception) {}
            micController.releaseAny()

            if (stateMachine.transition(VoiceState.RECOVERING)) notifyState()

            mainHandler.postDelayed({
                stateMachine.recoverTo(VoiceState.WAKE_LISTENING)
                notifyState()
                if (wakeEnabled) {
                    val ok = wakeEngine.startMonitoring()
                    if (ok && stateMachine.transition(VoiceState.WAKE_LISTENING)) notifyState()
                } else {
                    if (stateMachine.transition(VoiceState.DISABLED)) notifyState()
                }
            }, ERROR_RECOVERY_MS * 3)
        }
    }

    /**
     * Speaks assistant response via TTS.
     * During SPEAKING, interrupt detector runs to detect user interruption.
     * After TTS completes, returns to wake mode.
     */
    fun speakResponse(text: String, onComplete: () -> Unit = {}) {
        mainHandler.removeCallbacks(processingTimeoutRunnable)
        val generation = sessionGeneration.get()

        audioRouteManager.deactivateVoiceRouting()
        if (text.isBlank()) {
            resumeWakeAfterCommand()
            onComplete()
            return
        }

        if (!stateMachine.isSpeaking) {
            if (!stateMachine.transition(VoiceState.SPEAKING)) {
                Log.d(TAG, "Recovering SPEAKING from ${stateMachine.state}")
                stateMachine.recoverTo(VoiceState.SPEAKING)
            }
            notifyState()
        }

        // Start interrupt detection during TTS
        interruptDetector.start()

        ttsEngine.speak(text) {
            // Stale callback guard
            if (generation != sessionGeneration.get()) {
                Log.d(TAG, "Stale TTS callback ignored (gen=$generation)")
                return@speak
            }

            Log.i(TAG, "Response TTS complete")
            interruptDetector.stop()
            mainHandler.postDelayed({
                audioRouteManager.ensureNormalAudioMode()
                onComplete()
                resumeWakeAfterCommand()
            }, 100L)
        }
    }

    /**
     * Interrupt detected during TTS.
     * Flow: stop TTS → play acknowledgement beep → start listening
     */
    private fun handleInterruptDetected() {
        if (!stateMachine.isSpeaking && !stateMachine.isProcessing) {
            Log.d(TAG, "Interrupt detected but not speaking/processing — ignoring")
            return
        }

        val generation = sessionGeneration.incrementAndGet()
        Log.i(TAG, "Interrupt detected — gen=$generation")

        // Transition to INTERRUPTING
        if (!stateMachine.transition(VoiceState.INTERRUPTING)) {
            Log.w(TAG, "Cannot enter INTERRUPTING from ${stateMachine.state}")
            return
        }
        notifyState()

        // Stop TTS immediately
        ttsEngine.stop()
        interruptDetector.stop()

        // Play acknowledgement beep (option B: stop + acknowledge + listen)
        playBeep()

        // Start listening immediately after beep
        mainHandler.post {
            startListeningForCommand()
        }
    }

    /**
     * Manual interrupt entry point — can be called from service or UI.
     */
    fun interrupt() {
        if (!stateMachine.isSpeaking && !stateMachine.isProcessing) {
            Log.d(TAG, "interrupt() called but not in interruptible state")
            return
        }
        handleInterruptDetected()
    }

    fun setSpeechRate(rate: Float) { ttsEngine.setSpeechRate(rate) }

    /**
     * After command cycle finishes, atomically reset session lock
     * and return mic to wake-word detector.
     */
    private fun resumeWakeAfterCommand() {
        mainHandler.removeCallbacks(processingTimeoutRunnable)
        wakeSessionActive.set(false)
        lastWakeAcceptedAtMs = 0L
        interruptDetector.stop()

        if (!wakeEnabled) {
            if (stateMachine.state != VoiceState.DISABLED) {
                stateMachine.recoverTo(VoiceState.DISABLED)
            }
            notifyState()
            return
        }

        if (!stateMachine.isWakeListening) {
            if (!stateMachine.transition(VoiceState.WAKE_LISTENING)) {
                stateMachine.recoverTo(VoiceState.WAKE_LISTENING)
            }
        }
        notifyState()

        if (wakeEngine.isMonitoringNow) {
            wakeEngine.resume()
        } else {
            wakeEngine.startMonitoring()
        }
    }

    fun stopRuntime() {
        mainHandler.removeCallbacksAndMessages(null)
        wakeSessionActive.set(false)
        interruptDetector.stop()
        audioCapture.stop()
        speechController.destroy()
        wakeEngine.stopMonitoring()
        audioSessionManager.endSession()
        ttsEngine.stop()
        if (stateMachine.state != VoiceState.DISABLED) {
            stateMachine.recoverTo(VoiceState.DISABLED)
        }
        notifyState()
        Log.i(TAG, "VoiceRuntime stopped")
    }

    fun release() {
        mainHandler.removeCallbacksAndMessages(null)
        wakeSessionActive.set(false)
        interruptDetector.stop()
        audioCapture.stop()
        speechController.destroy()
        wakeEngine.release()
        audioSessionManager.release()
        ttsEngine.shutdown()
        try { tone?.release() } catch (_: Exception) {}
        tone = null
        stateListener = null
        stateMachine.forceState(VoiceState.DISABLED)
        Log.i(TAG, "VoiceRuntime released")
    }

    private fun playBeep() {
        try {
            val beepTone = ToneGenerator(AudioManager.STREAM_SYSTEM, 50)
            beepTone.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
            mainHandler.postDelayed({
                try { beepTone.release() } catch (_: Exception) {}
            }, 100L)
        } catch (e: Exception) {
            Log.w(TAG, "Beep tone generation failed", e)
        }
    }

    private fun notifyState() { stateListener?.invoke(state) }

    fun getMicController(): MicController = micController
}
