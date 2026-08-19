package com.jarvis.assistant.brain

data class ExecutionPlan(
    val intent: JarvisIntent,
    val requiresConfirmation: Boolean = false,
    val confirmationPrompt: String = ""
)

class Planner {
    fun createPlan(intent: JarvisIntent): ExecutionPlan {
        return when (intent) {
            is JarvisIntent.CallContact -> ExecutionPlan(intent, requiresConfirmation = true, confirmationPrompt = "Confirm calling ${intent.contactName}?")
            is JarvisIntent.SendSms -> ExecutionPlan(intent, requiresConfirmation = true, confirmationPrompt = "Confirm sending SMS to ${intent.recipient}?")
            is JarvisIntent.SendWhatsApp -> ExecutionPlan(intent, requiresConfirmation = true, confirmationPrompt = "Confirm sending WhatsApp message to ${intent.contactName}?")
            else -> ExecutionPlan(intent, requiresConfirmation = false)
        }
    }
}
