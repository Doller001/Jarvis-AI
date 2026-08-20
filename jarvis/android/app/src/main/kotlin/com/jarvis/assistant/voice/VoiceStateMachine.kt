package com.jarvis.assistant.voice

enum class VoiceState {
    STOPPED, STARTING, WAKE_LISTENING, WAKE_DETECTED, COMMAND_LISTENING, PROCESSING, SPEAKING, ERROR
}

/**
 * Pure state machine for the voice pipeline. No Android imports — JVM-testable.
 *
 * Legal transitions:
 *   STOPPED -> STARTING -> WAKE_LISTENING -> WAKE_DETECTED -> COMMAND_LISTENING
 *       -> PROCESSING -> SPEAKING -> WAKE_LISTENING
 *   WAKE_LISTENING -> SPEAKING (async/late responses, e.g. cloud brain)
 *   Any state -> ERROR; ERROR -> WAKE_LISTENING (recovery) or -> STOPPED.
 */
class VoiceStateMachine(initial: VoiceState = VoiceState.STOPPED) {
    var state: VoiceState = initial
        private set

    val isListening: Boolean
        get() = state == VoiceState.WAKE_LISTENING || state == VoiceState.COMMAND_LISTENING

    /** Returns true if the transition was applied, false if it was illegal. */
    fun transition(to: VoiceState): Boolean {
        if (to == state) return true
        if (!isLegal(state, to)) return false
        state = to
        return true
    }

    fun recoverFromError(): Boolean =
        transition(VoiceState.WAKE_LISTENING) || state == VoiceState.WAKE_LISTENING

    private fun isLegal(from: VoiceState, to: VoiceState): Boolean = when (from) {
        VoiceState.STOPPED -> to == VoiceState.STARTING || to == VoiceState.COMMAND_LISTENING || to == VoiceState.ERROR
        VoiceState.STARTING -> to == VoiceState.WAKE_LISTENING || to == VoiceState.STOPPED || to == VoiceState.ERROR
        VoiceState.WAKE_LISTENING -> to == VoiceState.WAKE_DETECTED || to == VoiceState.COMMAND_LISTENING || to == VoiceState.PROCESSING || to == VoiceState.SPEAKING || to == VoiceState.STOPPED || to == VoiceState.ERROR
        VoiceState.WAKE_DETECTED -> to == VoiceState.COMMAND_LISTENING || to == VoiceState.PROCESSING || to == VoiceState.WAKE_LISTENING || to == VoiceState.STOPPED || to == VoiceState.ERROR
        VoiceState.COMMAND_LISTENING -> to == VoiceState.PROCESSING || to == VoiceState.WAKE_LISTENING || to == VoiceState.STOPPED || to == VoiceState.ERROR
        VoiceState.PROCESSING -> to == VoiceState.SPEAKING || to == VoiceState.WAKE_LISTENING || to == VoiceState.STOPPED || to == VoiceState.ERROR
        VoiceState.SPEAKING -> to == VoiceState.WAKE_LISTENING || to == VoiceState.COMMAND_LISTENING || to == VoiceState.STOPPED || to == VoiceState.ERROR
        VoiceState.ERROR -> to == VoiceState.WAKE_LISTENING || to == VoiceState.STOPPED || to == VoiceState.ERROR
    }
}