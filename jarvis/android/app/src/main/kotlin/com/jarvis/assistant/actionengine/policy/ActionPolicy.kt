package com.jarvis.assistant.actionengine.policy

import com.jarvis.assistant.actionengine.model.ActionStep
import com.jarvis.assistant.actionengine.model.RiskLevel

object ActionPolicy {

    /**
     * Determines whether an action step requires user confirmation before execution.
     * High risk actions (making calls, deleting data) ALWAYS require confirmation.
     * Medium risk actions (sending messages) require confirmation unless pre-approved.
     */
    fun requiresConfirmation(step: ActionStep): Boolean {
        if (step.requiresConfirmation) return true
        return when (step.riskLevel) {
            RiskLevel.HIGH -> true
            RiskLevel.MEDIUM -> true
            RiskLevel.LOW -> false
        }
    }

    /**
     * Generates a clear confirmation prompt text for spoken TTS or UI dialog.
     */
    fun getConfirmationPrompt(step: ActionStep): String {
        return when (step.action) {
            com.jarvis.assistant.actionengine.model.ActionType.MAKE_CALL -> {
                val number = step.parameters["number"] ?: "contact"
                "Kya aap $number ko call lagana chahte hain?"
            }
            com.jarvis.assistant.actionengine.model.ActionType.SEND_MESSAGE -> {
                val contact = step.parameters["contact"] ?: "contact"
                val message = step.parameters["message"] ?: ""
                "$contact ko message bhejne ke liye confirm karein: \"$message\""
            }
            else -> "Kya aap is action ko proceed karna chahte hain?"
        }
    }
}
