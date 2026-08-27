package com.jarvis.assistant.actionengine.planner

import com.jarvis.assistant.actionengine.model.*
import java.util.UUID

class LocalTaskPlanner {

    fun plan(command: String): TaskPlan? {
        val lower = command.lowercase().trim()
        val taskId = "task_${UUID.randomUUID().toString().take(8)}"
        val hasMultiActionMarker = lower.contains(" aur ") || lower.contains(" and ") || 
                                   lower.contains(" then ") || lower.contains("kholo aur") || 
                                   lower.contains("open and") || lower.contains("karke ") ||
                                   lower.contains(" or ")

        if (!hasMultiActionMarker) {
            return null
        }

        // Multi-action: "camera kholo aur selfie lo"
        if (lower.contains("camera") && (lower.contains("selfie") || lower.contains("front camera"))) {
            val steps = listOf(
                ActionStep("step_1", ActionType.OPEN_APP, mapOf("target" to "camera")),
                ActionStep("step_2", ActionType.WAIT, mapOf("durationMs" to 800L), prerequisites = listOf("step_1")),
                ActionStep("step_3", ActionType.TAKE_SELFIE, prerequisites = listOf("step_2"))
            )
            return TaskPlan(taskId = taskId, command = command, intent = "camera_selfie_flow", steps = steps)
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
                .replace(Regex("\\\\bka\\\\b"), "")
                .replace(Regex("\\\\bpar\\\\b"), "")
                .replace(Regex("\\\\bpe\\\\b"), "")
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
                        parameters = mapOf("durationMs" to 500L),
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
                steps.add(
                    ActionStep(
                        actionId = "step_4",
                        action = ActionType.WAIT,
                        parameters = mapOf("durationMs" to 500L),
                        prerequisites = listOf("step_3")
                    )
                )
                steps.add(
                    ActionStep(
                        actionId = "step_5",
                        action = ActionType.CLICK_ELEMENT,
                        parameters = mapOf("target" to "first_video_result"),
                        prerequisites = listOf("step_4")
                    )
                )
            }
            return TaskPlan(taskId = taskId, command = command, intent = "youtube_play_flow", steps = steps)
        }

        // Multi-action: "Samsung Music kholo aur song play karo"
        if ((lower.contains("music") || lower.contains("gaana") || lower.contains("gana")) &&
            (lower.contains("play") || lower.contains("bajao") || lower.contains("chalao"))) {
            val steps = listOf(
                ActionStep("step_1", ActionType.OPEN_APP, mapOf("target" to "samsung music")),
                ActionStep("step_2", ActionType.WAIT, mapOf("durationMs" to 1000L), prerequisites = listOf("step_1")),
                ActionStep("step_3", ActionType.PLAY_MEDIA, prerequisites = listOf("step_2"))
            )
            return TaskPlan(taskId = taskId, command = command, intent = "samsung_music_play_flow", steps = steps)
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

        // Multi-action: "WhatsApp kholo aur unread messages padho"
        if (lower.contains("whatsapp") &&
            (lower.contains("read") || lower.contains("unread") || lower.contains("padho") || lower.contains("message dekho")) &&
            !(lower.contains("send") || lower.contains("bhejo") || lower.contains("kaho"))) {
            val steps = listOf(
                ActionStep("step_1", ActionType.OPEN_APP, mapOf("target" to "whatsapp")),
                ActionStep("step_2", ActionType.WAIT, mapOf("durationMs" to 1200L), prerequisites = listOf("step_1")),
                ActionStep("step_3", ActionType.READ_WHATSAPP_UNREAD, mapOf("target" to "whatsapp"), prerequisites = listOf("step_2"))
            )
            return TaskPlan(taskId = taskId, command = command, intent = "whatsapp_read_flow", steps = steps)
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

        // "Torch on aur volume badhao"
        if ((lower.contains("torch") || lower.contains("flashlight")) && 
            (lower.contains("volume") || lower.contains("awaz") || lower.contains("awaaz"))) {
            val torchState = if (lower.contains("off") || lower.contains("band") || lower.contains("bujhao")) "off" else "on"
            val volumeLevel = if (lower.contains("kam") || lower.contains("dheere") || lower.contains("down") || lower.contains("low") || lower.contains("ghatao")) 30 else 80
            val steps = listOf(
                ActionStep("step_1", ActionType.TOGGLE_TORCH, mapOf("state" to torchState)),
                ActionStep("step_2", ActionType.VOLUME_SET, mapOf("level" to volumeLevel))
            )
            return TaskPlan(taskId = taskId, command = command, intent = "torch_volume_flow", steps = steps)
        }

        // ===== NEW TASK FLOW: "Morning routine" =====
        if (lower.contains("morning routine") || lower.contains("subah ki routine") || 
            lower.contains("good morning") || lower.contains("brushing")/* simple*/) {
            val steps = listOf(
                ActionStep("step_1", ActionType.SET_BRIGHTNESS, mapOf("level" to 80)),
                ActionStep("step_2", ActionType.VOLUME_SET, mapOf("level" to 50), prerequisites = listOf("step_1")),
                ActionStep("step_3", ActionType.GET_DAILY_BRIEFING, mapOf("raw" to "morning"), prerequisites = listOf("step_2"))
            )
            return TaskPlan(taskId = taskId, command = command, intent = "morning_routine", steps = steps)
        }

        // ===== NEW TASK FLOW: "Night mode" =====
        if (lower.contains("shayad mode") || lower.contains("neend") || lower.contains("raat ki routine") || 
            lower.contains("night routine") || lower.contains("sleep mode")) {
            val steps = listOf(
                ActionStep("step_1", ActionType.BRIGHTNESS_DOWN, mapOf("level" to 20)),
                ActionStep("step_2", ActionType.SET_RINGER, mapOf("mode" to "silent"), prerequisites = listOf("step_1")),
                ActionStep("step_3", ActionType.TOGGLE_DND, mapOf("state" to "on"), prerequisites = listOf("step_2"))
            )
            return TaskPlan(taskId = taskId, command = command, intent = "night_mode", steps = steps)
        }

        // ===== NEW TASK FLOW: "Meeting mode" =====
        if (lower.contains("meeting mode") || lower.contains("room me enter") || 
            lower.contains("class mode") || lower.contains("silent mode")) {
            val steps = listOf(
                ActionStep("step_1", ActionType.SET_RINGER, mapOf("mode" to "silent")),
                ActionStep("step_2", ActionType.TOGGLE_DND, mapOf("state" to "on"), prerequisites = listOf("step_1"))
            )
            return TaskPlan(taskId = taskId, command = command, intent = "meeting_mode", steps = steps)
        }

        // ===== NEW TASK FLOW: "Movie mode" =====
        if (lower.contains("movie") && (lower.contains("mode") || lower.contains("chalao"))) {
            val steps = listOf(
                ActionStep("step_1", ActionType.OPEN_APP, mapOf("target" to "youtube")),
                ActionStep("step_2", ActionType.SET_BRIGHTNESS, mapOf("level" to 100), prerequisites = listOf("step_1")),
                ActionStep("step_3", ActionType.VOLUME_SET, mapOf("level" to 80), prerequisites = listOf("step_2"))
            )
            return TaskPlan(taskId = taskId, command = command, intent = "movie_mode", steps = steps)
        }

        // ===== NEW TASK FLOW: "Battery low check + actions" =====
        if (lower.contains("battery") && (lower.contains("check") || lower.contains("low") || lower.contains("khatam"))) {
            val steps = listOf(
                ActionStep("step_1", ActionType.GET_LOCATION, mapOf("raw" to "battery check")),
                ActionStep("step_2", ActionType.GET_DAILY_BRIEFING, mapOf("raw" to "battery low"), prerequisites = listOf("step_1"))
            )
            return TaskPlan(taskId = taskId, command = command, intent = "battery_alert_flow", steps = steps)
        }

        // "WiFi chalu aur YouTube kholo"
        if ((lower.contains("wifi on") || lower.contains("wifi chalu") || lower.contains("wifi")) &&
            lower.contains("youtube") && (lower.contains("kholo") || lower.contains("open") || lower.contains("chalao"))) {
            val steps = listOf(
                ActionStep("step_1", ActionType.TOGGLE_WIFI, mapOf("state" to "on")),
                ActionStep("step_2", ActionType.OPEN_APP, mapOf("target" to "youtube"), prerequisites = listOf("step_1"))
            )
            return TaskPlan(taskId = taskId, command = command, intent = "wifi_youtube_flow", steps = steps)
        }

        return null
    }

    private fun extractContact(text: String): String? {
        val hindiMatch = Regex("(?i)\\b([a-zA-Z0-9_]+)\\s+ko\\b").find(text)
        if (hindiMatch != null) {
            val candidate = hindiMatch.groupValues[1].trim()
            if (candidate.lowercase() !in listOf("whatsapp", "app", "kholo", "aur", "and", "open", "then")) {
                return candidate
            }
        }
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
        val hindiMatch = Regex("(?i)\\bko\\s+(.+?)\\s*(?:bhejo|send|message|kaho)\\s*$").find(text)
        if (hindiMatch != null) {
            val candidate = hindiMatch.groupValues[1].trim()
            if (candidate.isNotBlank()) return candidate
        }
        val hindiFallback = Regex("(?i)\\bko\\s+(.+)$").find(text)
        if (hindiFallback != null) {
            val candidate = hindiFallback.groupValues[1].trim()
            if (candidate.isNotBlank()) return candidate
        }
        val englishMatch = Regex("(?i)\\b(?:send|message|saying|text)\\s+(.+?)\\s+\\bto\\s+[a-zA-Z0-9_]+\\b").find(text)
        if (englishMatch != null) {
            val candidate = englishMatch.groupValues[1].trim()
            if (candidate.isNotBlank()) return candidate
        }
        val englishFallback = Regex("(?i)\\bto\\s+[a-zA-Z0-9_]+\\s+(.+)$").find(text)
        if (englishFallback != null) {
            val candidate = englishFallback.groupValues[1].trim()
            if (candidate.isNotBlank()) return candidate
        }
        return null
    }
}