package com.jarvis.assistant.voice

enum class VoiceState {
    IDLE,
    WAKE,        // always-listening for the wake word ("Hey Jarvis")
    LISTENING,   // active command recognition after a wake event
    PROCESSING,
    SPEAKING,
    ERROR
}

/**
 * Pure state machine for the Jarvis voice pipeline (Phase 1 & 2 architecture).
 *
 * Normal Flow (wake-word enabled):
 *   IDLE -> WAKE -> LISTENING -> PROCESSING -> SPEAKING -> WAKE/IDLE
 *
 * Push-to-talk / no wake-word Flow:
 *   IDLE -> LISTENING -> PROCESSING -> SPEAKING -> IDLE
 *
 * Error & Recovery:
 *   WAKE / LISTENING / PROCESSING -> ERROR -> IDLE
 *
 * Cancellation:
 *   WAKE / LISTENING -> IDLE
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
        VoiceState.IDLE -> to == VoiceState.WAKE || to == VoiceState.LISTENING || to == VoiceState.PROCESSING || to == VoiceState.ERROR
        VoiceState.WAKE -> to == VoiceState.LISTENING || to == VoiceState.IDLE || to == VoiceState.ERROR
        VoiceState.LISTENING -> to == VoiceState.PROCESSING || to == VoiceState.IDLE || to == VoiceState.ERROR
        VoiceState.PROCESSING -> to == VoiceState.SPEAKING || to == VoiceState.IDLE || to == VoiceState.ERROR
        VoiceState.SPEAKING -> to == VoiceState.IDLE || to == VoiceState.WAKE || to == VoiceState.LISTENING || to == VoiceState.ERROR
        VoiceState.ERROR -> to == VoiceState.IDLE || to == VoiceState.WAKE || to == VoiceState.LISTENING || to == VoiceState.ERROR
    }
}