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
        if ((lower.contains("youtube") || lower.contains("yt")) && 
            (lower.contains("search") || lower.contains("chalao") || lower.contains("bajao") || lower.contains("play") || lower.contains("kholo") || lower.contains("open"))) {
            
            val secondPart = when {
                lower.contains(" aur ") -> lower.substringAfter(" aur ")
                lower.contains(" and ") -> lower.substringAfter(" and ")
                lower.contains(" then ") -> lower.substringAfter(" then ")
                lower.contains("kholo aur") -> lower.substringAfter("kholo aur")
                lower.contains("open and") -> lower.substringAfter("open and")
                else -> lower
            }

            var query = secondPart
                .replace(Regex("^(jarvis|hey jarvis)\\s+"), "")
                .replace(Regex("^(open|kholo|play|search)\\s+"), "")
                .replace(Regex("(youtube|yt)\\s*(pe|par|mein|me)?\\s*"), "")
                .replace("search karo", "")
                .replace("search", "")
                .replace("chalao", "")
                .replace("bajao", "")
                .replace("play", "")
                .replace("gaana", "")
                .replace("gana", "")
                .replace("song", "")
                .replace(Regex("\\bka\\b"), "")
                .replace(Regex("\\bpar\\b"), "")
                .replace(Regex("\\bpe\\b"), "")
                .trim()

            if (query.isBlank() && secondPart.isNotBlank()) {
                query = secondPart.trim()
            }

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

        // Multi-action: "Chrome kholo aur <query> search karo"
        if ((lower.contains("chrome") || lower.contains("browser") || lower.contains("google")) && 
            (lower.contains("search") || lower.contains("dhoondo") || lower.contains("kholo") || lower.contains("open"))) {
            
            val secondPart = when {
                lower.contains(" aur ") -> lower.substringAfter(" aur ")
                lower.contains(" and ") -> lower.substringAfter(" and ")
                lower.contains(" then ") -> lower.substringAfter(" then ")
                else -> lower
            }

            val query = secondPart
                .replace(Regex("^(jarvis|hey jarvis)\\s+"), "")
                .replace(Regex("^(open|kholo|search)\\s+"), "")
                .replace(Regex("(chrome|browser|google)\\s*(pe|par|mein|me)?\\s*"), "")
                .replace("search karo", "")
                .replace("search", "")
                .replace("dhoondo", "")
                .trim()

            val steps = mutableListOf<ActionStep>()
            steps.add(
                ActionStep(
                    actionId = "step_1",
                    action = ActionType.OPEN_APP,
                    parameters = mapOf("target" to "chrome"),
                    expectedState = ExpectedState(StateType.APP_FOREGROUND, "Chrome in foreground")
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
                        parameters = mapOf("text" to query, "target" to "chrome"),
                        prerequisites = listOf("step_2")
                    )
                )
            }
            return TaskPlan(taskId = taskId, command = command, intent = "chrome_search_flow", steps = steps)
        }

        // Multi-action: "WhatsApp kholo aur <contact> ko <message> bhejo"
        if (lower.contains("whatsapp") && (lower.contains("send") || lower.contains("bhejo") || lower.contains("message") || lower.contains("kaho"))) {
            val contact = extractContact(command) ?: "contact"
            val message = extractMessage(command) ?: "Hello"

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
        if ((lower.contains("torch") || lower.contains("flashlight")) && 
            (lower.contains("volume") || lower.contains("awaaz") || lower.contains("awaz"))) {
            
            val torchState = if (lower.contains("off") || lower.contains("band") || lower.contains("bujhao")) "off" else "on"
            val volumeLevel = if (lower.contains("kam") || lower.contains("dheere") || lower.contains("down") || lower.contains("low") || lower.contains("ghatao")) 30 else 80

            val steps = listOf(
                ActionStep(
                    actionId = "step_1",
                    action = ActionType.TOGGLE_TORCH,
                    parameters = mapOf("state" to torchState)
                ),
                ActionStep(
                    actionId = "step_2",
                    action = ActionType.VOLUME_SET,
                    parameters = mapOf("level" to volumeLevel),
                    prerequisites = listOf("step_1")
                )
            )
            return TaskPlan(taskId = taskId, command = command, intent = "system_torch_volume_flow", steps = steps)
        }

        return null
    }

    private fun extractContact(text: String): String? {
        // Pattern 1 (Hindi): "... <contact> ko ..."
        val hindiMatch = Regex("(?i)\\b([a-zA-Z0-9_]+)\\s+ko\\b").find(text)
        if (hindiMatch != null) {
            val candidate = hindiMatch.groupValues[1].trim()
            if (candidate.lowercase() !in listOf("whatsapp", "app", "kholo", "aur", "and", "open", "then")) {
                return candidate
            }
        }
        // Pattern 2 (English): "to <contact>"
        val englishMatch = Regex("(?i)\\bto\\s+([a-zA-Z0-9_]+)").find(text)
        if (englishMatch != null) {
            val candidate = englishMatch.groupValues[1].trim()
            if (candidate.lowercase() !in listOf("whatsapp", "app")) {
                return candidate
            }
        }
        return null
    }

    private fun extractMessage(text: String): String? {
        // Pattern 1 (Hindi): "... ko <message> (bhejo|send|message|kaho)"
        val hindiMatch = Regex("(?i)\\bko\\s+(.+?)\\s*(?:bhejo|send|message|kaho)\\s*$").find(text)
        if (hindiMatch != null) {
            val candidate = hindiMatch.groupValues[1].trim()
            if (candidate.isNotBlank()) return candidate
        }
        // If no trailing verb, take everything after "ko"
        val hindiFallback = Regex("(?i)\\bko\\s+(.+)$").find(text)
        if (hindiFallback != null) {
            val candidate = hindiFallback.groupValues[1].trim()
            if (candidate.isNotBlank()) return candidate
        }
        // Pattern 2 (English): "send <message> to <contact>"
        val englishMatch = Regex("(?i)\\b(?:send|message|saying|text)\\s+(.+?)\\s+\\bto\\s+[a-zA-Z0-9_]+").find(text)
        if (englishMatch != null) {
            val candidate = englishMatch.groupValues[1].trim()
            if (candidate.isNotBlank()) return candidate
        }
        // Pattern 3 (English): "to <contact> <message>"
        val englishFallback = Regex("(?i)\\bto\\s+[a-zA-Z0-9_]+\\s+(.+)$").find(text)
        if (englishFallback != null) {
            val candidate = englishFallback.groupValues[1].trim()
            if (candidate.isNotBlank()) return candidate
        }
        return null
    }
}
