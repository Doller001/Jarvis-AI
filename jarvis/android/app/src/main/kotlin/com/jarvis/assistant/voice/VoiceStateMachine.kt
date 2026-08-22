package com.jarvis.assistant.voice

enum class VoiceState {
    STOPPED, STARTING, COMMAND_LISTENING, PROCESSING, SPEAKING, ERROR
}

/**
 * Pure state machine for the voice pipeline. No Android imports — JVM-testable.
 *
 * Legal transitions:
 *   STOPPED -> STARTING -> COMMAND_LISTENING -> PROCESSING -> SPEAKING -> STOPPED
 *   COMMAND_LISTENING -> STOPPED / ERROR
 *   PROCESSING -> SPEAKING -> STOPPED
 *   SPEAKING -> COMMAND_LISTENING / STOPPED
 *   Any state -> ERROR; ERROR -> STOPPED or COMMAND_LISTENING (recovery).
 */
class VoiceStateMachine(initial: VoiceState = VoiceState.STOPPED) {
    var state: VoiceState = initial
        private set

    val isListening: Boolean
        get() = state == VoiceState.COMMAND_LISTENING

    /** Returns true if the transition was applied, false if it was illegal. */
    fun transition(to: VoiceState): Boolean {
        if (to == state) return true
        if (!isLegal(state, to)) return false
        state = to
        return true
    }

    fun recoverFromError(): Boolean =
        transition(VoiceState.STOPPED) || state == VoiceState.STOPPED

    private fun isLegal(from: VoiceState, to: VoiceState): Boolean = when (from) {
        VoiceState.STOPPED -> to == VoiceState.STARTING || to == VoiceState.COMMAND_LISTENING || to == VoiceState.ERROR
        VoiceState.STARTING -> to == VoiceState.COMMAND_LISTENING || to == VoiceState.STOPPED || to == VoiceState.ERROR
        VoiceState.COMMAND_LISTENING -> to == VoiceState.PROCESSING || to == VoiceState.STOPPED || to == VoiceState.ERROR
        VoiceState.PROCESSING -> to == VoiceState.SPEAKING || to == VoiceState.STOPPED || to == VoiceState.ERROR
        VoiceState.SPEAKING -> to == VoiceState.COMMAND_LISTENING || to == VoiceState.STOPPED || to == VoiceState.ERROR
        VoiceState.ERROR -> to == VoiceState.STOPPED || to == VoiceState.COMMAND_LISTENING || to == VoiceState.ERROR
    }
}