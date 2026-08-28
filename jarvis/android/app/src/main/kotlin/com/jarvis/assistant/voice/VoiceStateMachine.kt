package com.jarvis.assistant.voice

import android.util.Log
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Hardened voice state machine.
 *
 * IMPORTANT:
 * - No forceState()
 * - No recoverTo()
 * - No arbitrary state jumps
 *
 * Normal command-listening can ONLY be entered through:
 *   1. WakeWordConfirmed
 *   2. ManualCommandStart
 *   3. BargeInInterrupt
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

enum class CommandTrigger {
    WAKE_WORD,
    MANUAL_BUTTON,
    BARGE_IN
}

sealed class VoiceEvent {
    data object EnableWake : VoiceEvent()
    data object DisableWake : VoiceEvent()

    data class CommandRequested(
        val trigger: CommandTrigger
    ) : VoiceEvent()

    data object WakeConfirmed : VoiceEvent()
    data object WakeCaptureReleased : VoiceEvent()

    data object SpeechFinished : VoiceEvent()
    data object ProcessingFinished : VoiceEvent()

    data object TtsStarted : VoiceEvent()
    data object TtsFinished : VoiceEvent()

    data object InterruptDetected : VoiceEvent()

    data object Timeout : VoiceEvent()
    data object Error : VoiceEvent()

    data object RecoveryComplete : VoiceEvent()
    data object Shutdown : VoiceEvent()
}

class VoiceStateMachine(
    initial: VoiceState = VoiceState.DISABLED
) {

    companion object {
        private const val TAG = "VoiceStateMachine"

        /**
         * State transitions are intentionally strict.
         *
         * NOTE:
         * COMMAND_LISTENING is not globally reachable.
         * Trigger validation is additionally enforced by VoiceRuntime.
         */
        private val TRANSITIONS: Map<VoiceState, Set<VoiceState>> = mapOf(

            VoiceState.DISABLED to setOf(
                VoiceState.WAKE_LISTENING,
                VoiceState.COMMAND_LISTENING,
                VoiceState.RECOVERING
            ),

            VoiceState.WAKE_LISTENING to setOf(
                VoiceState.ACKNOWLEDGING,
                VoiceState.COMMAND_LISTENING,
                VoiceState.DISABLED,
                VoiceState.RECOVERING
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

    @Volatile
    var state: VoiceState = initial
        private set

    val isWakeListening: Boolean
        get() = state == VoiceState.WAKE_LISTENING

    val isCommandListening: Boolean
        get() = state == VoiceState.COMMAND_LISTENING

    val isSpeaking: Boolean
        get() = state == VoiceState.SPEAKING

    val isInterrupting: Boolean
        get() = state == VoiceState.INTERRUPTING

    val isProcessing: Boolean
        get() = state == VoiceState.PROCESSING

    val isRecovering: Boolean
        get() = state == VoiceState.RECOVERING

    val isDisabled: Boolean
        get() = state == VoiceState.DISABLED

    fun transition(to: VoiceState): Boolean = lock.withLock {
        val from = state
        val allowed = TRANSITIONS[from].orEmpty()

        if (to in allowed) {
            state = to
            Log.d(TAG, "VALID [$from -> $to]")
            true
        } else {
            Log.w(
                TAG,
                "REJECTED [$from -> $to] allowed=${allowed.joinToString()}"
            )
            false
        }
    }

    /**
     * Event-based validation.
     *
     * Destination states must NEVER be selected directly for command mode.
     */
    fun dispatch(event: VoiceEvent): Boolean = lock.withLock {
        val current = state

        val target = when (event) {

            VoiceEvent.EnableWake -> {
                when (current) {
                    VoiceState.DISABLED,
                    VoiceState.RECOVERING -> VoiceState.WAKE_LISTENING
                    else -> null
                }
            }

            VoiceEvent.DisableWake,
            VoiceEvent.Shutdown -> {
                if (current != VoiceState.DISABLED) {
                    VoiceState.DISABLED
                } else {
                    null
                }
            }

            is VoiceEvent.CommandRequested -> {
                when {
                    event.trigger == CommandTrigger.MANUAL_BUTTON &&
                            current in setOf(
                        VoiceState.DISABLED,
                        VoiceState.WAKE_LISTENING
                    ) -> VoiceState.COMMAND_LISTENING

                    event.trigger == CommandTrigger.WAKE_WORD &&
                            current == VoiceState.ACKNOWLEDGING -> {
                        VoiceState.COMMAND_LISTENING
                    }

                    event.trigger == CommandTrigger.BARGE_IN &&
                            current == VoiceState.INTERRUPTING -> {
                        VoiceState.COMMAND_LISTENING
                    }

                    else -> null
                }
            }

            VoiceEvent.WakeConfirmed -> {
                if (current == VoiceState.WAKE_LISTENING) {
                    VoiceState.ACKNOWLEDGING
                } else {
                    null
                }
            }

            VoiceEvent.WakeCaptureReleased -> {
                if (current == VoiceState.ACKNOWLEDGING) {
                    VoiceState.COMMAND_LISTENING
                } else {
                    null
                }
            }

            VoiceEvent.SpeechFinished -> {
                if (current == VoiceState.COMMAND_LISTENING) {
                    VoiceState.PROCESSING
                } else {
                    null
                }
            }

            VoiceEvent.ProcessingFinished -> {
                if (current == VoiceState.PROCESSING) {
                    VoiceState.SPEAKING
                } else {
                    null
                }
            }

            VoiceEvent.TtsStarted -> {
                if (current == VoiceState.PROCESSING) {
                    VoiceState.SPEAKING
                } else {
                    null
                }
            }

            VoiceEvent.TtsFinished -> {
                if (current == VoiceState.SPEAKING) {
                    VoiceState.WAKE_LISTENING
                } else {
                    null
                }
            }

            VoiceEvent.InterruptDetected -> {
                when (current) {
                    VoiceState.SPEAKING,
                    VoiceState.PROCESSING -> VoiceState.INTERRUPTING
                    else -> null
                }
            }

            VoiceEvent.Timeout,
            VoiceEvent.Error -> {
                when (current) {
                    VoiceState.DISABLED -> VoiceState.DISABLED
                    VoiceState.WAKE_LISTENING -> VoiceState.RECOVERING
                    VoiceState.ACKNOWLEDGING -> VoiceState.RECOVERING
                    VoiceState.COMMAND_LISTENING -> VoiceState.RECOVERING
                    VoiceState.PROCESSING -> VoiceState.RECOVERING
                    VoiceState.SPEAKING -> VoiceState.RECOVERING
                    VoiceState.INTERRUPTING -> VoiceState.RECOVERING
                    VoiceState.RECOVERING -> VoiceState.RECOVERING
                }
            }

            VoiceEvent.RecoveryComplete -> {
                when (current) {
                    VoiceState.RECOVERING -> VoiceState.WAKE_LISTENING
                    else -> null
                }
            }
        }

        if (target == null) {
            Log.w(TAG, "EVENT REJECTED event=$event state=$current")
            return false
        }

        state = target
        Log.d(TAG, "EVENT $event [$current -> $target]")
        true
    }
}


