package com.jarvis.assistant.voice

import android.util.Log

/**
 * Voice pipeline states (Phase 7 rebuild).
 *
 * DISABLED         — wake setting is OFF; no mic activity
 * WAKE_LISTENING   — only ONNX wake-word detector runs
 * ACKNOWLEDGING    — TTS "Yes Boss" playing; mic OFF
 * COMMAND_LISTENING— SpeechRecognizer active; wake detector OFF
 * PROCESSING       — utterance routed to brain
 * SPEAKING         — TTS playing response; wake detector OFF
 * RECOVERING       — transient error state; resetting resources
 * IDLE             — legacy fallback / push-to-talk rest state
 * ERROR            — unrecoverable within normal cycle
 */
enum class VoiceState {
    DISABLED,
    WAKE_LISTENING,
    ACKNOWLEDGING,
    COMMAND_LISTENING,
    PROCESSING,
    SPEAKING,
    RECOVERING,
    // Legacy aliases kept so existing callers compile unchanged.
    IDLE,   // maps to DISABLED / rest in push-to-talk mode
    WAKE,   // alias for WAKE_LISTENING
    LISTENING, // alias for COMMAND_LISTENING
    ERROR
}

/**
 * Strict state machine for the Jarvis voice pipeline.
 *
 * Normal wake-word flow:
 *   DISABLED → WAKE_LISTENING → ACKNOWLEDGING → COMMAND_LISTENING
 *             → PROCESSING → SPEAKING → WAKE_LISTENING
 *
 * Push-to-talk flow:
 *   IDLE → LISTENING → PROCESSING → SPEAKING → IDLE
 *
 * Invalid events are silently REJECTED — callbacks are never fired.
 */
class VoiceStateMachine(initial: VoiceState = VoiceState.IDLE) {
    companion object {
        private const val TAG = "VoiceStateMachine"
    }

    @Volatile
    var state: VoiceState = initial
        private set

    val isListening: Boolean
        get() = state == VoiceState.LISTENING || state == VoiceState.COMMAND_LISTENING

    val isIdle: Boolean
        get() = state == VoiceState.IDLE || state == VoiceState.DISABLED

    val isWakeListening: Boolean
        get() = state == VoiceState.WAKE_LISTENING || state == VoiceState.WAKE

    val isCommandListening: Boolean
        get() = state == VoiceState.COMMAND_LISTENING || state == VoiceState.LISTENING

    /**
     * Returns true if the transition was applied, false if it was illegal.
     * Illegal transitions are silently rejected — caller must check return value.
     */
    fun transition(to: VoiceState): Boolean {
        if (to == state) return true
        if (!isLegal(state, to)) {
            Log.w(TAG, "REJECTED transition $state → $to")
            return false
        }
        Log.d(TAG, "$state → $to")
        state = to
        return true
    }

    /** Recovers from error/recovering back to IDLE resting state. */
    fun recoverFromError(): Boolean {
        return if (state == VoiceState.IDLE || state == VoiceState.DISABLED) {
            true
        } else {
            val ok = transition(VoiceState.IDLE)
            if (!ok) {
                state = VoiceState.IDLE
                Log.w(TAG, "Force-reset to IDLE from $state")
            }
            true
        }
    }

    private fun isLegal(from: VoiceState, to: VoiceState): Boolean = when (from) {
        // ── New canonical states ──────────────────────────────────────────────
        VoiceState.DISABLED ->
            to == VoiceState.WAKE_LISTENING || to == VoiceState.IDLE ||
            to == VoiceState.WAKE || to == VoiceState.ERROR

        VoiceState.WAKE_LISTENING ->
            to == VoiceState.ACKNOWLEDGING || to == VoiceState.DISABLED ||
            to == VoiceState.IDLE || to == VoiceState.ERROR || to == VoiceState.RECOVERING

        VoiceState.ACKNOWLEDGING ->
            to == VoiceState.COMMAND_LISTENING || to == VoiceState.WAKE_LISTENING ||
            to == VoiceState.DISABLED || to == VoiceState.IDLE ||
            to == VoiceState.ERROR || to == VoiceState.RECOVERING

        VoiceState.COMMAND_LISTENING ->
            to == VoiceState.PROCESSING || to == VoiceState.WAKE_LISTENING ||
            to == VoiceState.DISABLED || to == VoiceState.IDLE ||
            to == VoiceState.ERROR || to == VoiceState.RECOVERING

        VoiceState.PROCESSING ->
            to == VoiceState.SPEAKING || to == VoiceState.WAKE_LISTENING ||
            to == VoiceState.DISABLED || to == VoiceState.IDLE ||
            to == VoiceState.ERROR || to == VoiceState.RECOVERING

        VoiceState.SPEAKING ->
            to == VoiceState.WAKE_LISTENING || to == VoiceState.DISABLED ||
            to == VoiceState.IDLE || to == VoiceState.ERROR ||
            to == VoiceState.RECOVERING
            // SPEAKING → COMMAND_LISTENING intentionally FORBIDDEN
            // (prevents TTS feedback loops)

        VoiceState.RECOVERING ->
            to == VoiceState.WAKE_LISTENING || to == VoiceState.DISABLED ||
            to == VoiceState.IDLE || to == VoiceState.ERROR

        // ── Legacy aliases (keep existing callers compiling) ──────────────────
        VoiceState.IDLE ->
            to == VoiceState.WAKE || to == VoiceState.WAKE_LISTENING ||
            to == VoiceState.LISTENING || to == VoiceState.COMMAND_LISTENING ||
            to == VoiceState.PROCESSING || to == VoiceState.SPEAKING ||
            to == VoiceState.DISABLED || to == VoiceState.ERROR

        VoiceState.WAKE ->
            to == VoiceState.LISTENING || to == VoiceState.COMMAND_LISTENING ||
            to == VoiceState.ACKNOWLEDGING || to == VoiceState.IDLE ||
            to == VoiceState.DISABLED || to == VoiceState.SPEAKING ||
            to == VoiceState.PROCESSING || to == VoiceState.ERROR || to == VoiceState.RECOVERING

        VoiceState.LISTENING ->
            to == VoiceState.PROCESSING || to == VoiceState.SPEAKING ||
            to == VoiceState.IDLE || to == VoiceState.WAKE_LISTENING ||
            to == VoiceState.DISABLED || to == VoiceState.ERROR || to == VoiceState.RECOVERING

        VoiceState.ERROR ->
            to == VoiceState.IDLE || to == VoiceState.WAKE_LISTENING ||
            to == VoiceState.WAKE || to == VoiceState.LISTENING ||
            to == VoiceState.DISABLED || to == VoiceState.SPEAKING ||
            to == VoiceState.RECOVERING || to == VoiceState.ERROR
    }
}
