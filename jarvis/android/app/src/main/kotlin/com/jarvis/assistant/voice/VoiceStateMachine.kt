package com.jarvis.assistant.voice

import android.util.Log
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Canonical voice state machine with synchronized transitions.
 *
 * States:
 *   DISABLED → WAKE_LISTENING → ACKNOWLEDGING → COMMAND_LISTENING
 *            → PROCESSING → SPEAKING → WAKE_LISTENING / DISABLED
 *
 * Manual command path:
 *   DISABLED / WAKE_LISTENING + ManualCommandStart → COMMAND_LISTENING
 *
 * Interrupt path:
 *   SPEAKING → INTERRUPTING → COMMAND_LISTENING
 *   PROCESSING → INTERRUPTING → COMMAND_LISTENING
 *
 * Recovery:
 *   ANY ERROR → RECOVERING → WAKE_LISTENING / DISABLED
 */
enum class VoiceState {
    DISABLED,
    WAKE_LISTENING,
    ACKNOWLEDGING,
    COMMAND_LISTENING,
    PROCESSING,
    SPEAKING,
    INTERRUPTING,
    RECOVERING
}

sealed class VoiceEvent {
    object ManualCommandStart : VoiceEvent()
    object WakeDetected : VoiceEvent()
    object WakeAcknowledged : VoiceEvent()
    object SpeechComplete : VoiceEvent()
    object TtsDone : VoiceEvent()
    object Interrupt : VoiceEvent()
    object TimeoutOrError : VoiceEvent()
    object RecoveryDone : VoiceEvent()
}

class VoiceStateMachine(initial: VoiceState = VoiceState.DISABLED) {

    companion object {
        private const val TAG = "VoiceStateMachine"

        private val TRANSITIONS: Map<VoiceState, Set<VoiceState>> = mapOf(
            VoiceState.DISABLED to setOf(
                VoiceState.WAKE_LISTENING,
                VoiceState.COMMAND_LISTENING // Manual Command Button
            ),
            VoiceState.WAKE_LISTENING to setOf(
                VoiceState.ACKNOWLEDGING,
                VoiceState.COMMAND_LISTENING, // Manual Command Button or Direct Trigger
                VoiceState.DISABLED
            ),
            VoiceState.ACKNOWLEDGING to setOf(
                VoiceState.COMMAND_LISTENING,
                VoiceState.WAKE_LISTENING,
                VoiceState.DISABLED,
                VoiceState.RECOVERING
            ),
            VoiceState.COMMAND_LISTENING to setOf(
                VoiceState.PROCESSING,
                VoiceState.WAKE_LISTENING,
                VoiceState.DISABLED,
                VoiceState.RECOVERING
            ),
            VoiceState.PROCESSING to setOf(
                VoiceState.SPEAKING,
                VoiceState.WAKE_LISTENING,
                VoiceState.DISABLED,
                VoiceState.INTERRUPTING,
                VoiceState.RECOVERING
            ),
            VoiceState.SPEAKING to setOf(
                VoiceState.WAKE_LISTENING,
                VoiceState.DISABLED,
                VoiceState.INTERRUPTING,
                VoiceState.RECOVERING
            ),
            VoiceState.INTERRUPTING to setOf(
                VoiceState.COMMAND_LISTENING,
                VoiceState.WAKE_LISTENING,
                VoiceState.DISABLED,
                VoiceState.RECOVERING
            ),
            VoiceState.RECOVERING to setOf(
                VoiceState.WAKE_LISTENING,
                VoiceState.DISABLED
            )
        )
    }

    private val lock = ReentrantLock()
    @Volatile var state: VoiceState = initial
        private set

    val isWakeListening: Boolean get() = state == VoiceState.WAKE_LISTENING
    val isCommandListening: Boolean get() = state == VoiceState.COMMAND_LISTENING
    val isSpeaking: Boolean get() = state == VoiceState.SPEAKING
    val isInterrupting: Boolean get() = state == VoiceState.INTERRUPTING
    val isProcessing: Boolean get() = state == VoiceState.PROCESSING
    val isRecovering: Boolean get() = state == VoiceState.RECOVERING
    val isDisabled: Boolean get() = state == VoiceState.DISABLED

    fun transition(to: VoiceState): Boolean = lock.withLock {
        val from = state
        val allowed = TRANSITIONS[from]
        if (allowed != null && to in allowed) {
            state = to
            Log.d(TAG, "[$from → $to]")
            true
        } else {
            Log.w(TAG, "ILLEGAL [$from → $to] — allowed: ${allowed?.joinToString() ?: "none"}")
            false
        }
    }

    fun recoverTo(to: VoiceState): Boolean = lock.withLock {
        val prev = state
        state = to
        Log.w(TAG, "FORCE RECOVER [$prev → $to]")
        true
    }

    fun forceState(to: VoiceState) = lock.withLock {
        val prev = state
        state = to
        Log.w(TAG, "FORCE [$prev → $to]")
    }
}

