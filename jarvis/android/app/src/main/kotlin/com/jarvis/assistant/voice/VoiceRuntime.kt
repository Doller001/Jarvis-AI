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
 * Central voice coordinator with strict CommandTrigger validation,
 * atomic mic handoff, session generation tracking, and hardened recovery.
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

    private val voiceLock = Any()

    @Volatile
    private var commandSessionActive = false

    @Volatile
    private var activeCommandTrigger: CommandTrigger? = null

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
            if (stateMachine.dispatch(VoiceEvent.EnableWake)) notifyState()
            Log.i(TAG, "Wake mode ACTIVATED")
        } else {
            Log.w(TAG, "Wake engine failed to start — staying in current state")
        }
    }

    private fun deactivateWakeMode() {
        synchronized(voiceLock) {
            wakeEnabled = false

            wakeEngine.stopMonitoring()
            interruptDetector.stop()
            speechController.destroy()

            if (audioCapture.isCapturing()) {
                audioCapture.stop()
            }

            micController.releaseMic(
                MicController.OWNER_STT
            )

            micController.releaseMic(
                MicController.OWNER_WAKE
            )

            stateMachine.dispatch(
                VoiceEvent.DisableWake
            )

            commandSessionActive = false
            activeCommandTrigger = null
            wakeSessionActive.set(false)

            notifyState()

            Log.i(
                TAG,
                "Wake mode disabled safely"
            )
        }
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
     * Wake event gate — strict checks before action.
     */
    private fun handleWakeEvent() {
        synchronized(voiceLock) {
            val now = SystemClock.elapsedRealtime()

            if (!wakeEnabled) {
                Log.d(
                    TAG,
                    "WAKE_REJECT reason=wake_disabled"
                )
                return
            }

            if (stateMachine.state != VoiceState.WAKE_LISTENING) {
                Log.d(
                    TAG,
                    "WAKE_REJECT reason=invalid_state state=${stateMachine.state}"
                )
                return
            }

            val owner = micController.getCurrentOwner()

            if (
                owner != null &&
                owner != MicController.OWNER_WAKE
            ) {
                Log.d(
                    TAG,
                    "WAKE_REJECT reason=mic_busy owner=$owner"
                )
                return
            }

            if (
                now - lastWakeAcceptedAtMs <
                WAKE_COOLDOWN_MS
            ) {
                Log.d(
                    TAG,
                    "WAKE_REJECT reason=cooldown"
                )
                return
            }

            if (
                !wakeSessionActive.compareAndSet(false, true)
            ) {
                Log.d(
                    TAG,
                    "WAKE_REJECT reason=session_active"
                )
                return
            }

            if (
                !stateMachine.dispatch(
                    VoiceEvent.WakeConfirmed
                )
            ) {
                wakeSessionActive.set(false)

                Log.w(
                    TAG,
                    "WAKE_REJECT reason=state_machine"
                )

                return
            }

            lastWakeAcceptedAtMs = now

            val generation =
                sessionGeneration.incrementAndGet()

            VoiceDiagnostics.logSessionGeneration(
                generation
            )

            VoiceDiagnostics.logLifecycleEvent(
                "WAKE_CONFIRMED generation=$generation"
            )

            notifyState()

            mainHandler.post {
                beginWakeToSttHandoff(generation)
            }
        }
    }

    private fun beginWakeToSttHandoff(
        generation: Long
    ) {
        synchronized(voiceLock) {
            if (generation != sessionGeneration.get()) {
                Log.d(
                    TAG,
                    "WAKE_HANDOFF_ABORT stale generation=$generation"
                )
                return
            }

            if (
                stateMachine.state !=
                VoiceState.ACKNOWLEDGING
            ) {
                Log.w(
                    TAG,
                    "WAKE_HANDOFF_ABORT state=${stateMachine.state}"
                )
                wakeSessionActive.set(false)
                return
            }

            Log.i(
                TAG,
                "WAKE_HANDOFF begin generation=$generation"
            )

            // 1. Completely stop wake engine.
            wakeEngine.pause()

            // 2. Verify physical wake capture stopped.
            if (!wakeEngine.isAudioRecordReleased) {
                Log.w(
                    TAG,
                    "WAKE_HANDOFF waiting for AudioRecord release"
                )

                mainHandler.postDelayed(
                    {
                        beginWakeToSttHandoff(
                            generation
                        )
                    },
                    50L
                )

                return
            }

            // 3. Wake must no longer own logical mic.
            if (
                micController.isOwnedBy(
                    MicController.OWNER_WAKE
                )
            ) {
                micController.releaseMic(
                    MicController.OWNER_WAKE
                )
            }

            // 4. Verify again.
            val ownerAfterRelease =
                micController.getCurrentOwner()

            if (ownerAfterRelease != null) {
                Log.w(
                    TAG,
                    "WAKE_HANDOFF_ABORT mic still owned by=$ownerAfterRelease"
                )
                return
            }

            // 5. Now, and ONLY now, enter command state.
            if (
                !stateMachine.dispatch(
                    VoiceEvent.CommandRequested(
                        CommandTrigger.WAKE_WORD
                    )
                )
            ) {
                Log.w(
                    TAG,
                    "WAKE_HANDOFF_ABORT state transition rejected"
                )

                wakeSessionActive.set(false)
                return
            }

            commandSessionActive = true
            activeCommandTrigger = CommandTrigger.WAKE_WORD

            notifyState()

            Log.i(
                TAG,
                "WAKE_HANDOFF complete -> COMMAND_LISTENING"
            )

            audioSessionManager.beginSession()

            startRecognition(
                trigger = CommandTrigger.WAKE_WORD,
                generation = generation
            )
        }
    }

    fun setWakeSensitivity(label: String) {
        val value = when (label.lowercase()) {
            "low" -> 0.35f
            "high" -> 0.8f
            else -> 0.5f
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
     */
    fun startManualCommand() {
        Log.i(
            TAG,
            "MANUAL_MIC_REQUEST state=${stateMachine.state}"
        )

        requestCommandListening(
            CommandTrigger.MANUAL_BUTTON
        )
    }

    private fun requestCommandListening(
        trigger: CommandTrigger
    ) {
        synchronized(voiceLock) {
            val currentState = stateMachine.state

            Log.i(
                TAG,
                "COMMAND_REQUEST trigger=$trigger state=$currentState"
            )

            // ---------------------------------------------------------
            // HARD SECURITY GATE
            // ---------------------------------------------------------
            val allowed = when (trigger) {
                CommandTrigger.WAKE_WORD ->
                    currentState == VoiceState.ACKNOWLEDGING && wakeEnabled

                CommandTrigger.MANUAL_BUTTON ->
                    currentState == VoiceState.DISABLED ||
                            currentState == VoiceState.WAKE_LISTENING

                CommandTrigger.BARGE_IN ->
                    currentState == VoiceState.INTERRUPTING
            }

            if (!allowed) {
                Log.w(
                    TAG,
                    "COMMAND_REQUEST_REJECTED trigger=$trigger state=$currentState"
                )

                VoiceDiagnostics.logLifecycleEvent(
                    "COMMAND_REJECT trigger=$trigger state=$currentState"
                )

                return
            }

            // Prevent duplicate sessions.
            if (commandSessionActive) {
                Log.w(
                    TAG,
                    "COMMAND_REQUEST_REJECTED reason=session_already_active"
                )
                return
            }

            val generation = sessionGeneration.incrementAndGet()

            // ---------------------------------------------------------
            // STOP EVERYTHING THAT CAN COMPETE FOR AUDIO
            // ---------------------------------------------------------
            interruptDetector.stop()

            if (audioCapture.isCapturing()) {
                audioCapture.stop()
            }

            if (trigger == CommandTrigger.WAKE_WORD) {
                wakeEngine.pause()
            }

            // TTS may only be stopped for an interrupt.
            if (trigger == CommandTrigger.BARGE_IN) {
                ttsEngine.stop()
            }

            // ---------------------------------------------------------
            // VERIFY MIC IS REALLY FREE
            // ---------------------------------------------------------
            val currentOwner = micController.getCurrentOwner()

            if (
                currentOwner != null &&
                currentOwner != MicController.OWNER_STT
            ) {
                Log.w(
                    TAG,
                    "COMMAND_REQUEST_REJECTED micOwner=$currentOwner"
                )
                return
            }

            // ---------------------------------------------------------
            // STATE TRANSITION
            // ---------------------------------------------------------
            val stateChanged = when (trigger) {
                CommandTrigger.WAKE_WORD -> {
                    stateMachine.dispatch(
                        VoiceEvent.CommandRequested(
                            CommandTrigger.WAKE_WORD
                        )
                    )
                }

                CommandTrigger.MANUAL_BUTTON -> {
                    stateMachine.dispatch(
                        VoiceEvent.CommandRequested(
                            CommandTrigger.MANUAL_BUTTON
                        )
                    )
                }

                CommandTrigger.BARGE_IN -> {
                    stateMachine.dispatch(
                        VoiceEvent.CommandRequested(
                            CommandTrigger.BARGE_IN
                        )
                    )
                }
            }

            if (!stateChanged) {
                Log.w(
                    TAG,
                    "COMMAND_REQUEST_REJECTED state transition failed trigger=$trigger"
                )
                return
            }

            commandSessionActive = true
            activeCommandTrigger = trigger

            notifyState()

            VoiceDiagnostics.logLifecycleEvent(
                "COMMAND_START trigger=$trigger generation=$generation"
            )

            audioSessionManager.beginSession()

            startRecognition(
                trigger = trigger,
                generation = generation
            )
        }
    }

    private fun startRecognition(
        trigger: CommandTrigger,
        generation: Long
    ) {
        mainHandler.removeCallbacks(commandTimeoutRunnable)

        mainHandler.postDelayed(
            commandTimeoutRunnable,
            COMMAND_TIMEOUT_MS
        )

        val request = CommandListeningRequest(
            reason = when (trigger) {
                CommandTrigger.WAKE_WORD ->
                    TriggerReason.WakeWordConfirmed

                CommandTrigger.MANUAL_BUTTON ->
                    TriggerReason.ManualButton

                CommandTrigger.BARGE_IN ->
                    TriggerReason.BargeInInterrupt
            },
            sessionId = generation
        )

        // Final runtime guard BEFORE SpeechRecognizer.
        val state = stateMachine.state

        if (state != VoiceState.COMMAND_LISTENING) {
            Log.e(
                TAG,
                "STT_START_ABORT invalid state=$state trigger=$trigger"
            )

            commandSessionActive = false
            activeCommandTrigger = null

            handleError(
                android.speech.SpeechRecognizer.ERROR_CLIENT,
                "Invalid voice state before STT start"
            )

            return
        }

        if (
            trigger == CommandTrigger.WAKE_WORD &&
            wakeEngine.isMonitoringNow
        ) {
            Log.e(
                TAG,
                "STT_START_ABORT wake engine still monitoring"
            )

            commandSessionActive = false
            activeCommandTrigger = null

            handleError(
                android.speech.SpeechRecognizer.ERROR_CLIENT,
                "Wake engine still owns microphone"
            )

            return
        }

        VoiceDiagnostics.logLifecycleEvent(
            "STT_AUTHORIZED trigger=$trigger generation=$generation"
        )

        speechController.startListening(
            request = request,

            onResult = { utterance ->
                onCommandReceived(
                    utterance,
                    generation
                )
            },

            onError = { errorCode, errorMessage ->
                Log.w(
                    TAG,
                    "STT error code=$errorCode message=$errorMessage"
                )

                if (generation != sessionGeneration.get()) {
                    Log.d(
                        TAG,
                        "Ignoring stale STT error generation=$generation"
                    )
                    return@startListening
                }

                handleError(
                    errorCode,
                    errorMessage
                )
            },

            onRmsChanged = { rms ->
                onRmsChanged?.invoke(rms)
            }
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

        if (!stateMachine.dispatch(VoiceEvent.SpeechFinished)) {
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
        errorCode: Int =
            android.speech.SpeechRecognizer.ERROR_CLIENT,
        reason: String
    ) {
        synchronized(voiceLock) {
            Log.w(
                TAG,
                "VOICE_ERROR code=$errorCode reason=$reason"
            )

            VoiceDiagnostics.logError(
                errorCode
            )

            mainHandler.removeCallbacks(
                commandTimeoutRunnable
            )

            mainHandler.removeCallbacks(
                processingTimeoutRunnable
            )

            commandSessionActive = false
            activeCommandTrigger = null

            speechController.destroy()

            audioSessionManager.endSession()

            interruptDetector.stop()

            if (audioCapture.isCapturing()) {
                audioCapture.stop()
            }

            wakeEngine.pause()

            micController.releaseMic(
                MicController.OWNER_STT
            )

            micController.releaseMic(
                MicController.OWNER_WAKE
            )

            val enteredRecovery =
                stateMachine.dispatch(
                    VoiceEvent.Error
                )

            if (enteredRecovery) {
                notifyState()
            }

            sessionGeneration.incrementAndGet()

            mainHandler.postDelayed(
                {
                    synchronized(voiceLock) {
                        wakeSessionActive.set(false)
                        lastWakeAcceptedAtMs = 0L

                        if (!wakeEnabled) {
                            stateMachine.transition(
                                VoiceState.DISABLED
                            )
                            notifyState()
                            return@synchronized
                        }

                        stateMachine.dispatch(
                            VoiceEvent.RecoveryComplete
                        )

                        notifyState()

                        val started =
                            wakeEngine.startMonitoring()

                        if (!started) {
                            Log.e(
                                TAG,
                                "VOICE_RECOVERY wake engine failed"
                            )
                        }
                    }
                },
                ERROR_RECOVERY_MS
            )
        }
    }

    private fun handleVoiceEngineFailure(
        component: String,
        error: Throwable
    ) {
        synchronized(voiceLock) {
            Log.e(
                TAG,
                "VOICE_ENGINE_FAILURE component=$component",
                error
            )

            sessionGeneration.incrementAndGet()

            commandSessionActive = false
            activeCommandTrigger = null
            wakeSessionActive.set(false)

            try {
                audioCapture.stop()
            } catch (_: Exception) {
            }

            try {
                interruptDetector.stop()
            } catch (_: Exception) {
            }

            try {
                speechController.destroy()
            } catch (_: Exception) {
            }

            try {
                wakeEngine.stopMonitoring()
            } catch (_: Exception) {
            }

            try {
                audioRouteManager.deactivateVoiceRouting()
            } catch (_: Exception) {
            }

            micController.releaseMic(
                MicController.OWNER_STT
            )

            micController.releaseMic(
                MicController.OWNER_WAKE
            )

            stateMachine.dispatch(
                VoiceEvent.Error
            )

            notifyState()

            mainHandler.postDelayed(
                {
                    synchronized(voiceLock) {
                        if (!wakeEnabled) {
                            stateMachine.transition(
                                VoiceState.DISABLED
                            )
                            notifyState()
                            return@synchronized
                        }

                        stateMachine.dispatch(
                            VoiceEvent.RecoveryComplete
                        )

                        notifyState()

                        val ok =
                            wakeEngine.startMonitoring()

                        if (!ok) {
                            Log.e(
                                TAG,
                                "Wake engine recovery failed"
                            )
                        }
                    }
                },
                1000L
            )
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
            if (!stateMachine.dispatch(VoiceEvent.TtsStarted)) {
                stateMachine.transition(VoiceState.SPEAKING)
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
        synchronized(voiceLock) {
            val currentState = stateMachine.state

            if (
                currentState != VoiceState.SPEAKING &&
                currentState != VoiceState.PROCESSING
            ) {
                Log.d(
                    TAG,
                    "INTERRUPT_REJECT state=$currentState"
                )
                return
            }

            if (commandSessionActive) {
                Log.d(
                    TAG,
                    "INTERRUPT_REJECT command already active"
                )
                return
            }

            val generation =
                sessionGeneration.incrementAndGet()

            Log.i(
                TAG,
                "INTERRUPT_CONFIRMED generation=$generation"
            )

            if (
                !stateMachine.dispatch(
                    VoiceEvent.InterruptDetected
                )
            ) {
                Log.w(
                    TAG,
                    "INTERRUPT_REJECT state-machine"
                )
                return
            }

            notifyState()

            ttsEngine.stop()
            interruptDetector.stop()

            // Make sure no other audio capture is running.
            if (audioCapture.isCapturing()) {
                audioCapture.stop()
            }

            playBeep()

            mainHandler.postDelayed(
                {
                    synchronized(voiceLock) {
                        if (
                            generation !=
                            sessionGeneration.get()
                        ) {
                            Log.d(
                                TAG,
                                "INTERRUPT_STALE generation=$generation"
                            )
                            return@synchronized
                        }

                        requestCommandListening(
                            CommandTrigger.BARGE_IN
                        )
                    }
                },
                120L
            )
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
        synchronized(voiceLock) {
            mainHandler.removeCallbacks(
                processingTimeoutRunnable
            )

            commandSessionActive = false
            activeCommandTrigger = null

            wakeSessionActive.set(false)
            lastWakeAcceptedAtMs = 0L

            interruptDetector.stop()

            speechController.destroy()

            audioSessionManager.endSession()

            if (!wakeEnabled) {
                stateMachine.transition(
                    VoiceState.DISABLED
                )

                notifyState()
                return
            }

            // Current state must be safe before wake resumes.
            if (
                stateMachine.state !=
                VoiceState.WAKE_LISTENING
            ) {
                when (stateMachine.state) {
                    VoiceState.COMMAND_LISTENING,
                    VoiceState.PROCESSING,
                    VoiceState.SPEAKING,
                    VoiceState.INTERRUPTING -> {
                        stateMachine.transition(
                            VoiceState.WAKE_LISTENING
                        )
                    }

                    VoiceState.RECOVERING -> {
                        stateMachine.dispatch(
                            VoiceEvent.RecoveryComplete
                        )
                    }

                    VoiceState.DISABLED -> {
                        stateMachine.dispatch(
                            VoiceEvent.EnableWake
                        )
                    }

                    else -> Unit
                }
            }

            notifyState()

            if (!wakeEngine.isMonitoringNow) {
                val started =
                    wakeEngine.startMonitoring()

                if (!started) {
                    Log.e(
                        TAG,
                        "Failed to restart wake-word monitoring"
                    )
                }
            } else {
                wakeEngine.resume()
            }

            Log.i(
                TAG,
                "VOICE_READY wake=$wakeEnabled state=${stateMachine.state}"
            )
        }
    }

    fun stopRuntime() {
        synchronized(voiceLock) {
            mainHandler.removeCallbacksAndMessages(null)
            wakeSessionActive.set(false)
            commandSessionActive = false
            activeCommandTrigger = null

            interruptDetector.stop()
            audioCapture.stop()
            speechController.destroy()
            wakeEngine.stopMonitoring()
            audioSessionManager.endSession()
            ttsEngine.stop()

            micController.releaseMic(
                MicController.OWNER_STT
            )

            micController.releaseMic(
                MicController.OWNER_WAKE
            )

            stateMachine.transition(VoiceState.DISABLED)
            notifyState()
            Log.i(TAG, "VoiceRuntime stopped")
        }
    }

    fun release() {
        synchronized(voiceLock) {
            mainHandler.removeCallbacksAndMessages(null)

            sessionGeneration.incrementAndGet()

            wakeSessionActive.set(false)
            commandSessionActive = false
            activeCommandTrigger = null

            interruptDetector.stop()
            audioCapture.stop()
            speechController.destroy()
            wakeEngine.release()
            audioSessionManager.release()
            ttsEngine.shutdown()

            micController.releaseMic(
                MicController.OWNER_STT
            )

            micController.releaseMic(
                MicController.OWNER_WAKE
            )

            stateMachine.transition(
                VoiceState.DISABLED
            )

            try {
                tone?.release()
            } catch (_: Exception) {
            }

            tone = null
            stateListener = null

            Log.i(
                TAG,
                "VoiceRuntime released safely"
            )
        }
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

