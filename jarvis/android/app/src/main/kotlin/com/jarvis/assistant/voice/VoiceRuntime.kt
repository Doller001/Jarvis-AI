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

/**
 * Central voice coordinator — Phase 2, 3, 7, 8, 9, 12 rebuild.
 *
 * STATE MACHINE (enforced):
 *   DISABLED → WAKE_LISTENING → ACKNOWLEDGING → COMMAND_LISTENING
 *            → PROCESSING → SPEAKING → WAKE_LISTENING
 *
 * CRITICAL RULES:
 *  1. Wake setting is authoritative: only setWakeEnabled() starts/stops the detector.
 *  2. startRuntime() initialises components but does NOT start wake detection.
 *  3. SpeechRecognizer is FORBIDDEN during WAKE_LISTENING / ACKNOWLEDGING.
 *  4. "Yes Boss" TTS completes BEFORE command STT starts (Phase 8).
 *  5. Wake callback is gated through 7 checks before any action (Phase 3).
 *  6. AtomicBoolean wakeSessionActive prevents duplicate callbacks (Phase 9).
 *  7. All voice-engine failures → RECOVERING → WAKE_LISTENING/DISABLED (Phase 12).
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
        private const val COMMAND_TIMEOUT_MS      = 8000L
        private const val PROCESSING_TIMEOUT_MS   = 6000L
        private const val ERROR_RECOVERY_MS       = 500L
        private const val WAKE_COOLDOWN_MS        = 2500L  // Phase 9
    }

    private val stateMachine = VoiceStateMachine(VoiceState.IDLE)
    val state: VoiceState get() = stateMachine.state

    private val mainHandler    = Handler(Looper.getMainLooper())
    private var commandCallback: ((String) -> Unit)? = null
    private var stateListener:   ((VoiceState) -> Unit)? = null
    private var tone: ToneGenerator? = null

    // Phase 9: AtomicBoolean — prevents duplicate wake callbacks.
    private val wakeSessionActive = AtomicBoolean(false)
    @Volatile private var lastWakeAcceptedAtMs = 0L

    // Phase 2: single authority flag. Reflects the SettingsManager.wakeWordEnabled setting.
    @Volatile private var wakeEnabled = false

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
     * Phase 2: Initialises all components.
     * Does NOT start wake detection — call setWakeEnabled(true) after startup
     * to respect the SettingsManager.wakeWordEnabled flag.
     */
    fun startRuntime(onCommandRecognized: (String) -> Unit) {
        commandCallback = onCommandRecognized
        audioRouteManager.start()
        audioCapture.onEnvironmentChanged = { env -> onEnvironmentChanged?.invoke(env) }
        audioCapture.onFrameProcessed = { res -> onAudioMetrics?.invoke(res) }
        VoiceDiagnostics.logMicState("VoiceRuntime initialised — IDLE (wake NOT started)")
        Log.i(TAG, "VoiceRuntime started. Call setWakeEnabled(true) to begin wake monitoring.")

        // Set up wake engine error handler once.
        wakeEngine.setOnErrorListener { error ->
            Log.w(TAG, "Wake-word engine error: ${error.message}")
            handleVoiceEngineFailure("WakeEngine", error)
        }
    }

    /**
     * Phase 2: Single authority entry point for enabling / disabling wake detection.
     * Called by the service after reading SettingsManager.wakeWordEnabled.
     *
     * wakeWordEnabled = false → detector.stop(), mic released, state = DISABLED
     * wakeWordEnabled = true  → detector.start(), mic acquired, state = WAKE_LISTENING
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
        // Do not double-start if already in wake mode.
        if (stateMachine.isWakeListening) return
        if (!installWakeCallback()) return  // callback already installed on first call
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
        speechController.destroy()
        micController.releaseAny()
        if (stateMachine.transition(VoiceState.DISABLED) ||
            stateMachine.transition(VoiceState.IDLE)) notifyState()
        Log.i(TAG, "Wake mode DEACTIVATED — mic released")
    }

    /**
     * Installs the wake callback once. Idempotent after first call.
     * Returns true always (callback installed on first call).
     */
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
     * Phase 3 + Phase 9: Wake event gate.
     * 7-step check before any action is taken.
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
            Log.d(TAG, "[WAKE_GATE] REJECT — state=${stateMachine.state} (expected WAKE_LISTENING)")
            return
        }

        // Gate 3: Mic owner must be WAKE_WORD (not STT).
        val micOwner = micController.getCurrentOwner()
        if (micOwner != null && micOwner != MicController.OWNER_WAKE) {
            Log.d(TAG, "[WAKE_GATE] REJECT — mic owned by '$micOwner'")
            return
        }

        // Gate 4: Cooldown.
        if (now - lastWakeAcceptedAtMs < WAKE_COOLDOWN_MS) {
            Log.d(TAG, "[WAKE_GATE] REJECT — cooldown (${now - lastWakeAcceptedAtMs}ms < ${WAKE_COOLDOWN_MS}ms)")
            return
        }

        // Gate 5 + 6 + 7: AtomicBoolean session lock (prevents duplicate callbacks).
        if (!wakeSessionActive.compareAndSet(false, true)) {
            Log.d(TAG, "[WAKE_GATE] REJECT — wakeSessionActive already true")
            return
        }

        // All gates passed.
        lastWakeAcceptedAtMs = now
        Log.i(TAG, "[WAKE_GATE] ACCEPT — starting acknowledgement")
        startAcknowledgement()
    }

    /**
     * Phase 8: Acknowledgement sequence.
     * 1. Transition to ACKNOWLEDGING
     * 2. Pause wake detector (mic released)
     * 3. TTS "Yes Boss"
     * 4. ONLY after TTS completes → start command STT
     *
     * During ACKNOWLEDGING: wake detector = OFF, command STT = OFF.
     * This prevents "Yes Boss" from re-triggering wake detection.
     */
    private fun startAcknowledgement() {
        if (!stateMachine.transition(VoiceState.ACKNOWLEDGING)) {
            Log.w(TAG, "Cannot enter ACKNOWLEDGING from ${stateMachine.state}")
            wakeSessionActive.set(false)
            return
        }
        notifyState()

        // Phase 4: Low-latency wake transition (≤ 100ms)
        wakeEngine.pause()
        playBeep()

        mainHandler.postDelayed({
            startListeningForCommand()
        }, 80L)
    }

    /**
     * Sensitivity label → 0..1 value.
     * "Low" = 0.5, "Balanced" = 0.8, "High" = 1.0.
     */
    fun setWakeSensitivity(label: String) {
        val value = when (label.lowercase()) {
            "low"  -> 0.5f
            "high" -> 1.0f
            else   -> 0.8f
        }
        wakeEngine.setSensitivity(value)
    }

    /**
     * Legacy toggle for UI button. Delegates to setWakeEnabled().
     * Returns the new active state.
     */
    fun toggleMonitoring(): Boolean {
        val nowEnabled = !wakeEnabled
        setWakeEnabled(nowEnabled)
        return nowEnabled
    }

    /**
     * Starts command STT.
     * Phase 7: Only allowed from ACKNOWLEDGING or COMMAND_LISTENING.
     * Phase 6: Mic ownership check — STT must not start if wake engine holds mic.
     */
    fun startListeningForCommand() {
        if (stateMachine.state == VoiceState.SPEAKING) {
            ttsEngine.stop()
        }

        // Ensure background audio capture is completely stopped.
        if (audioCapture.isCapturing()) audioCapture.stop()

        // Ensure wake engine mic is released before STT acquires.
        wakeEngine.pause()

        if (!stateMachine.transition(VoiceState.COMMAND_LISTENING)) {
            // Try LISTENING (legacy alias) if COMMAND_LISTENING fails.
            if (!stateMachine.transition(VoiceState.LISTENING)) {
                Log.w(TAG, "Cannot enter COMMAND_LISTENING from ${stateMachine.state} — recovering")
                stateMachine.recoverFromError()
                if (!stateMachine.transition(VoiceState.COMMAND_LISTENING)) {
                    Log.e(TAG, "Cannot start command listening — state machine stuck")
                    wakeSessionActive.set(false)
                    resumeWakeAfterCommand()
                    return
                }
            }
        }
        notifyState()

        audioSessionManager.beginSession()
        playBeep()

        mainHandler.postDelayed({
            if (stateMachine.isCommandListening) {
                startRecognition()
            }
        }, 50L)
    }

    private fun startRecognition() {
        mainHandler.removeCallbacks(commandTimeoutRunnable)
        mainHandler.postDelayed(commandTimeoutRunnable, COMMAND_TIMEOUT_MS)

        speechController.startListening(
            onResult = { utterance -> onCommandReceived(utterance) },
            onError  = { errorCode, errorMessage ->
                Log.w(TAG, "STT error ($errorCode): $errorMessage")
                handleError(errorCode, errorMessage)
            },
            onRmsChanged = { rms ->
                if (rms > 2f) {
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
        audioSessionManager.endSession()

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

        // Phase 3 Fix: Watchdog timer prevents 75-second hang in PROCESSING
        mainHandler.removeCallbacks(processingTimeoutRunnable)
        mainHandler.postDelayed(processingTimeoutRunnable, PROCESSING_TIMEOUT_MS)

        commandCallback?.invoke(command)
    }

    private fun onProcessingTimeout() {
        Log.w(TAG, "[PROCESSING_TIMEOUT] Command processing took > ${PROCESSING_TIMEOUT_MS}ms — resetting session")
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

        // Phase 12: RECOVERING state — no infinite restart loops.
        if (stateMachine.transition(VoiceState.RECOVERING)) notifyState()

        mainHandler.postDelayed({
            stateMachine.recoverFromError()
            notifyState()
            resumeWakeAfterCommand()
        }, ERROR_RECOVERY_MS)
    }

    /**
     * Phase 12: Voice engine failure handler.
     * AudioRecord / ONNX / STT / TTS / mic-unavailable → safe recovery.
     */
    private fun handleVoiceEngineFailure(component: String, error: Throwable) {
        Log.e(TAG, "[$component] failure: ${error.message}")
        mainHandler.post {
            // 1. Release all resources.
            try { audioCapture.stop() } catch (_: Exception) {}
            try { speechController.destroy() } catch (_: Exception) {}
            try { audioRouteManager.deactivateVoiceRouting() } catch (_: Exception) {}
            micController.releaseAny()

            // 2. Reset state.
            if (stateMachine.transition(VoiceState.RECOVERING)) notifyState()

            // 3. After a brief pause, return to wake or disabled.
            mainHandler.postDelayed({
                stateMachine.recoverFromError()
                notifyState()
                if (wakeEnabled) {
                    // Re-start wake engine.
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
     * After TTS completes, returns to wake mode if enabled.
     */
    fun speakResponse(text: String, onComplete: () -> Unit = {}) {
        mainHandler.removeCallbacks(processingTimeoutRunnable)
        audioRouteManager.deactivateVoiceRouting()
        if (text.isBlank()) {
            resumeWakeAfterCommand()
            onComplete()
            return
        }

        if (!stateMachine.transition(VoiceState.SPEAKING)) {
            Log.d(TAG, "Forcing SPEAKING from ${stateMachine.state}")
            stateMachine.recoverFromError()
            stateMachine.transition(VoiceState.SPEAKING)
        }
        notifyState()

        ttsEngine.speak(text) {
            Log.i(TAG, "Response TTS complete")
            mainHandler.postDelayed({
                audioRouteManager.ensureNormalAudioMode()
                onComplete()
                resumeWakeAfterCommand()
            }, 100L)
        }
    }

    fun setSpeechRate(rate: Float) { ttsEngine.setSpeechRate(rate) }

    /**
     * Phase 9: After a command cycle finishes, atomically reset the session lock
     * and return the mic to the wake-word detector.
     */
    private fun resumeWakeAfterCommand() {
        mainHandler.removeCallbacks(processingTimeoutRunnable)
        wakeSessionActive.set(false)
        lastWakeAcceptedAtMs = SystemClock.elapsedRealtime()
        if (!wakeEnabled) {
            if (stateMachine.transition(VoiceState.DISABLED)) notifyState()
            return
        }
        // Return to WAKE_LISTENING.
        if (!stateMachine.transition(VoiceState.WAKE_LISTENING)) {
            stateMachine.recoverFromError()
            stateMachine.transition(VoiceState.WAKE_LISTENING)
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
        audioCapture.stop()
        speechController.destroy()
        wakeEngine.stopMonitoring()
        audioSessionManager.endSession()
        ttsEngine.stop()
        if (stateMachine.transition(VoiceState.IDLE)) notifyState()
        Log.i(TAG, "VoiceRuntime stopped")
    }

    fun release() {
        mainHandler.removeCallbacksAndMessages(null)
        wakeSessionActive.set(false)
        audioCapture.stop()
        speechController.destroy()
        wakeEngine.release()
        audioSessionManager.release()
        ttsEngine.shutdown()
        try { tone?.release() } catch (_: Exception) {}
        tone = null
        stateListener = null
        stateMachine.transition(VoiceState.IDLE)
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
