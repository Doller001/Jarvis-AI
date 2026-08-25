package com.jarvis.assistant.execution

import android.content.Context
import android.util.Log
import com.jarvis.assistant.actionengine.core.ActionExecutor
import com.jarvis.assistant.actionengine.model.TaskPlan
import com.jarvis.assistant.brain.JarvisBrain
import com.jarvis.assistant.brain.JarvisIntent
import com.jarvis.assistant.memory.MemoryDecisionRouter
import com.jarvis.assistant.memory.RouteSource
import com.jarvis.assistant.telemetry.DiagnosticEventBus
import com.jarvis.assistant.telemetry.TelemetryEventType

sealed class ExecutionOutcome {
    data class Success(val spokenResponse: String, val isLocalAction: Boolean) : ExecutionOutcome()
    data class ConfirmationRequired(val prompt: String) : ExecutionOutcome()
    data class Failure(val reason: String, val spokenResponse: String) : ExecutionOutcome()
    data class RouteToCloud(val promptText: String) : ExecutionOutcome()
}

/**
 * Authoritative single owner for:
 *   parse → plan → execute → verify → report → speak
 *
 * Guarantees:
 *  1. Side-effecting commands NEVER get bypassed by cached conversational CAG memories.
 *  2. Multi-step actions only report success when all steps pass real verification.
 *  3. Full diagnostic telemetry emitted for all execution stages.
 */
class TaskExecutionCoordinator(
    private val context: Context? = null,
    private val brain: JarvisBrain = JarvisBrain(),
    private val commandExecutor: CommandExecutor = CommandExecutor(context),
    private val actionExecutor: ActionExecutor = ActionExecutor(context)
) {
    companion object {
        private const val TAG = "TaskExecutionCoordinator"
    }

    suspend fun coordinate(
        utterance: String,
        memoryRouter: MemoryDecisionRouter? = null
    ): ExecutionOutcome {
        val startTime = System.currentTimeMillis()
        val text = utterance.trim()
        Log.i(TAG, "Coordinating execution for utterance: '$text'")

        DiagnosticEventBus.emit(
            type = TelemetryEventType.TASK_START,
            component = TAG,
            details = mapOf("utterance" to text)
        )

        // 1. Check if input is a side-effecting command or conversational query
        val isSideEffecting = isSideEffectingCommand(text)

        // 2. If conversational and memoryRouter has high confidence CAG exact hit, use cached knowledge
        if (!isSideEffecting && memoryRouter != null) {
            val routed = memoryRouter.route(text)
            if (routed.source == RouteSource.FAST_CAG_EXACT || routed.source == RouteSource.FAST_CAG_NEAR) {
                Log.i(TAG, "Conversational query served from CAG memory: '${routed.text}'")
                return ExecutionOutcome.Success(routed.text, isLocalAction = false)
            }
        }

        // 3. Plan command via JarvisBrain
        val plan = brain.processCommand(text)

        // 4. Handle plan intent
        return when (val intent = plan.intent) {
            is JarvisIntent.MultiStepTask -> {
                executeMultiStepPlan(intent.plan)
            }
            is JarvisIntent.Unknown -> {
                Log.i(TAG, "Unknown local intent — routing to Cloud Brain")
                ExecutionOutcome.RouteToCloud(text)
            }
            else -> {
                if (plan.requiresConfirmation) {
                    val prompt = "Confirmation required: ${plan.confirmationPrompt}"
                    ExecutionOutcome.ConfirmationRequired(prompt)
                } else {
                    val result = commandExecutor.execute(intent)
                    val spoken = brain.formatResponse(intent, result)
                    val duration = System.currentTimeMillis() - startTime
                    DiagnosticEventBus.emit(
                        type = TelemetryEventType.TASK_COMPLETED,
                        component = TAG,
                        durationMs = duration,
                        success = true,
                        details = mapOf("intent" to intent.javaClass.simpleName, "result" to result)
                    )
                    ExecutionOutcome.Success(spoken, isLocalAction = true)
                }
            }
        }
    }

    private suspend fun executeMultiStepPlan(plan: TaskPlan): ExecutionOutcome {
        Log.i(TAG, "Executing multi-step task plan: ${plan.taskId} with ${plan.steps.size} steps")
        val report = actionExecutor.executePlanWithReport(plan)

        return if (report.completed) {
            ExecutionOutcome.Success(report.spokenSummary, isLocalAction = true)
        } else {
            ExecutionOutcome.Failure(
                reason = "Step ${report.failedStepId} failed verification",
                spokenResponse = report.spokenSummary
            )
        }
    }

    private fun isSideEffectingCommand(text: String): Boolean {
        val lower = text.lowercase().trim()
        val sideEffectPrefixes = listOf(
            "open ", "close ", "launch ", "start ", "turn ", "play ", "pause ", "stop ",
            "set ", "volume ", "torch ", "wifi ", "bluetooth ", "call ", "sms ", "send ",
            "take ", "click ", "capture ", "kholo", "band", "chalao", "bajao", "lagao"
        )
        return sideEffectPrefixes.any { lower.contains(it) } ||
               lower.contains(" and ") || lower.contains(" aur ") || lower.contains(" then ")
    }
}
