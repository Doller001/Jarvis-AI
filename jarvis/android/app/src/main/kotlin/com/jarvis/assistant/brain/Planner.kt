package com.jarvis.assistant.brain

data class ExecutionPlan(
    val intent: JarvisIntent,
    val requiresConfirmation: Boolean = false,
    val confirmationPrompt: String = ""
)

class Planner {
    fun createPlan(intent: JarvisIntent): ExecutionPlan {
        return when (intent) {
            is JarvisIntent.CallContact -> ExecutionPlan(
                intent = intent,
                requiresConfirmation = true,
                confirmationPrompt = "Confirm calling ${intent.contactName}? (Say 'yes' or 'confirm' to proceed)"
            )
            is JarvisIntent.SendSms -> ExecutionPlan(
                intent = intent,
                requiresConfirmation = true,
                confirmationPrompt = "Confirm sending SMS to ${intent.recipient} saying '${intent.message}'? (Say 'yes' or 'confirm' to proceed)"
            )
            is JarvisIntent.SendWhatsApp -> ExecutionPlan(
                intent = intent,
                requiresConfirmation = true,
                confirmationPrompt = "Confirm sending WhatsApp message to ${intent.contactName} saying '${intent.message}'? (Say 'yes' or 'confirm' to proceed)"
            )
            else -> ExecutionPlan(intent, requiresConfirmation = false)
        }
    }
}
