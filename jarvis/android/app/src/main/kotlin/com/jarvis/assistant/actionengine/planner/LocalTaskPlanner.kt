package com.jarvis.assistant.actionengine.planner

import com.jarvis.assistant.actionengine.model.*
import java.util.UUID

class LocalTaskPlanner {

    fun plan(command: String): TaskPlan? {
        val lower = command.lowercase().trim()
        val taskId = "task_${UUID.randomUUID().toString().take(8)}"
        val hasMultiActionMarker = lower.contains(" aur ") || lower.contains(" and ") || 
                                   lower.contains(" then ") || lower.contains("kholo aur") || 
                                   lower.contains("open and") || lower.contains("karke ")

        if (!hasMultiActionMarker) {
            return null
        }

        // Multi-action: "YouTube kholo aur <query> search/chalao"
        if ((lower.contains("youtube") || lower.contains("yt")) && (lower.contains("search") || lower.contains("chalao") || lower.contains("bajao") || lower.contains("play"))) {
            val query = lower
                .replace(Regex("^(jarvis|hey jarvis)\\s+"), "")
                .replace(Regex("^(open|kholo|play|search)\\s+"), "")
                .replace("youtube pe ", "")
                .replace("youtube par ", "")
                .replace("youtube mein ", "")
                .replace("youtube me ", "")
                .replace("youtube ", "")
                .replace("search karo", "")
                .replace("search", "")
                .replace("chalao", "")
                .replace("bajao", "")
                .replace("play", "")
                .replace("gaana", "")
                .replace("song", "")
                .trim()

            val steps = mutableListOf<ActionStep>()
            steps.add(
                ActionStep(
                    actionId = "step_1",
                    action = ActionType.OPEN_APP,
                    parameters = mapOf("target" to "youtube"),
                    expectedState = ExpectedState(StateType.APP_FOREGROUND, "YouTube in foreground")
                )
            )
            if (query.isNotBlank()) {
                steps.add(
                    ActionStep(
                        actionId = "step_2",
                        action = ActionType.WAIT,
                        parameters = mapOf("durationMs" to 1000L),
                        prerequisites = listOf("step_1")
                    )
                )
                steps.add(
                    ActionStep(
                        actionId = "step_3",
                        action = ActionType.SEARCH_TEXT,
                        parameters = mapOf("text" to query, "target" to "youtube"),
                        prerequisites = listOf("step_2")
                    )
                )
            }
            return TaskPlan(taskId = taskId, command = command, intent = "youtube_play_flow", steps = steps)
        }

        // Multi-action: "WhatsApp kholo aur <contact> ko <message> bhejo"
        if (lower.contains("whatsapp") && (lower.contains("send") || lower.contains("bhejo") || lower.contains("message"))) {
            val contact = extractContact(lower) ?: "contact"
            val message = extractMessage(lower) ?: "Hello"

            val steps = listOf(
                ActionStep(
                    actionId = "step_1",
                    action = ActionType.RESOLVE_CONTACT,
                    parameters = mapOf("contact" to contact)
                ),
                ActionStep(
                    actionId = "step_2",
                    action = ActionType.SEND_MESSAGE,
                    parameters = mapOf("contact" to contact, "message" to message, "target" to "whatsapp"),
                    requiresConfirmation = true,
                    riskLevel = RiskLevel.MEDIUM,
                    prerequisites = listOf("step_1")
                )
            )
            return TaskPlan(taskId = taskId, command = command, intent = "whatsapp_send_flow", steps = steps)
        }

        // Multi-action: "Torch on karo aur volume badhao"
        if ((lower.contains("torch") || lower.contains("flashlight")) && (lower.contains("volume") || lower.contains("awaaz"))) {
            val steps = listOf(
                ActionStep(
                    actionId = "step_1",
                    action = ActionType.TOGGLE_TORCH,
                    parameters = mapOf("state" to if (lower.contains("off") || lower.contains("band")) "off" else "on")
                ),
                ActionStep(
                    actionId = "step_2",
                    action = ActionType.VOLUME_SET,
                    parameters = mapOf("level" to 80),
                    prerequisites = listOf("step_1")
                )
            )
            return TaskPlan(taskId = taskId, command = command, intent = "system_torch_volume_flow", steps = steps)
        }

        return null
    }

    private fun extractContact(text: String): String? {
        val match = Regex("(?i)(?:to|ko)\\s+([a-zA-Z0-9_]+)").find(text)
        return match?.groupValues?.get(1)?.trim()
    }

    private fun extractMessage(text: String): String? {
        val match = Regex("(?i)(?:message|bhejo|saying|text)\\s+(.+)").find(text)
        return match?.groupValues?.get(1)?.trim()
    }
}
