package com.jarvis.assistant.voice

enum class VoiceState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR
}

/**
 * Pure state machine for the Jarvis voice pipeline (Phase 1 & 2 architecture).
 *
 * Normal Flow:
 *   IDLE -> LISTENING -> PROCESSING -> SPEAKING -> IDLE
 *
 * Error & Recovery:
 *   LISTENING -> ERROR -> IDLE
 *   PROCESSING -> ERROR -> IDLE
 *
 * Cancellation:
 *   LISTENING -> IDLE
 *   SPEAKING -> IDLE
 */
class VoiceStateMachine(initial: VoiceState = VoiceState.IDLE) {
    var state: VoiceState = initial
        private set

    val isListening: Boolean
        get() = state == VoiceState.LISTENING

    val isIdle: Boolean
        get() = state == VoiceState.IDLE

    /**
     * Returns true if the transition was applied, false if it was illegal.
     */
    fun transition(to: VoiceState): Boolean {
        if (to == state) return true
        if (!isLegal(state, to)) return false
        state = to
        return true
    }

    /**
     * Recovers from error back to IDLE resting state.
     */
    fun recoverFromError(): Boolean =
        transition(VoiceState.IDLE) || state == VoiceState.IDLE

    private fun isLegal(from: VoiceState, to: VoiceState): Boolean = when (from) {
        VoiceState.IDLE -> to == VoiceState.LISTENING || to == VoiceState.PROCESSING || to == VoiceState.ERROR
        VoiceState.LISTENING -> to == VoiceState.PROCESSING || to == VoiceState.IDLE || to == VoiceState.ERROR
        VoiceState.PROCESSING -> to == VoiceState.SPEAKING || to == VoiceState.IDLE || to == VoiceState.ERROR
        VoiceState.SPEAKING -> to == VoiceState.IDLE || to == VoiceState.LISTENING || to == VoiceState.ERROR
        VoiceState.ERROR -> to == VoiceState.IDLE || to == VoiceState.LISTENING || to == VoiceState.ERROR
    }
}