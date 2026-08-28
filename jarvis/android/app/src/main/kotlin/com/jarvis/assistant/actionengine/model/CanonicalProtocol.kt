package com.jarvis.assistant.actionengine.model

import com.google.gson.annotations.SerializedName

/**
 * Canonical Action Protocol - unified action model shared with backend.
 * This is the single source of truth for action representation.
 */
data class RetryConfig(
    @SerializedName("max_attempts") val maxAttempts: Int = 2,
    @SerializedName("backoff_ms") val backoffMs: Long = 500
)

data class VerificationConfig(
    val type: String = "none",  // device_state, app_foreground, media_playing, screen_contains, none
    val expected: Map<String, Any> = emptyMap()
)

data class CanonicalAction(
    val id: String,
    val type: String,  // e.g. "device.toggle_torch", "app.open", "automation.click"
    val parameters: Map<String, Any> = emptyMap(),
    @SerializedName("requires_confirmation") val requiresConfirmation: Boolean = false,
    @SerializedName("timeout_ms") val timeoutMs: Long = 8000,
    val retry: RetryConfig = RetryConfig(),
    val verification: VerificationConfig = VerificationConfig(),
    @SerializedName("depends_on") val dependsOn: List<String> = emptyList(),
    val metadata: Map<String, Any> = emptyMap()
) {
    val namespace: String
        get() = if (type.contains(".")) type.substringBefore(".") else ""

    val actionName: String
        get() = if (type.contains(".")) type.substringAfter(".") else type

    fun isDeviceAction(): Boolean = namespace in listOf("device", "automation")
    fun isServerAction(): Boolean = namespace == "server"
    fun requiresAccessibility(): Boolean = namespace == "automation"

    fun toActionType(): ActionType? {
        return when (type) {
            "device.toggle_torch" -> ActionType.TOGGLE_TORCH
            "device.toggle_wifi" -> ActionType.TOGGLE_WIFI
            "device.toggle_bluetooth" -> ActionType.TOGGLE_BLUETOOTH
            "device.set_volume" -> ActionType.VOLUME_SET
            "device.set_brightness" -> ActionType.BRIGHTNESS_SET
            "device.lock_screen" -> ActionType.CLOSE_APP  // Reuse for lock
            "app.open" -> ActionType.OPEN_APP
            "app.close" -> ActionType.CLOSE_APP
            "automation.click" -> ActionType.CLICK_ELEMENT
            "automation.type" -> ActionType.TYPE_TEXT
            "automation.swipe" -> ActionType.SWIPE
            "automation.search" -> ActionType.SEARCH_TEXT
            "automation.play_media" -> ActionType.PLAY_MEDIA
            "automation.take_selfie" -> ActionType.TAKE_SELFIE
            "automation.read_screen" -> ActionType.READ_SCREEN
            else -> null
        }
    }

    fun toActionStep(stepIndex: Int = 0): ActionStep {
        val actionType = toActionType() ?: ActionType.OPEN_APP
        return ActionStep(
            actionId = id,
            action = actionType,
            parameters = parameters.mapValues { it.value },
            timeoutMs = timeoutMs,
            maxRetries = retry.maxAttempts,
            retryDelaysMs = listOf(retry.backoffMs, retry.backoffMs * 2),
            prerequisites = dependsOn
        )
    }
}

/**
 * Action graph from backend - list of canonical actions with metadata.
 */
data class ActionGraph(
    val actions: List<CanonicalAction>,
    val metadata: Map<String, Any> = emptyMap()
) {
    fun getAction(actionId: String): CanonicalAction? =
        actions.find { it.id == actionId }

    fun getReadyActions(completedIds: Set<String>): List<CanonicalAction> =
        actions.filter { action ->
            action.id !in completedIds &&
            action.dependsOn.all { it in completedIds }
        }

    fun isComplete(completedIds: Set<String>): Boolean =
        actions.all { it.id in completedIds }

    fun toTaskPlan(command: String = "", intent: String = ""): TaskPlan {
        return TaskPlan(
            taskId = "plan-${System.currentTimeMillis()}",
            command = command,
            intent = intent,
            steps = actions.mapIndexed { index, action -> action.toActionStep(index) }
        )
    }
}
