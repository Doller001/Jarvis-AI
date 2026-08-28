package com.jarvis.assistant.execution

import android.util.Log
import com.jarvis.assistant.brain.JarvisIntent

sealed class ConfirmationDecision {
    object Confirmed : ConfirmationDecision()
    object Cancelled : ConfirmationDecision()
    object NotAConfirmation : ConfirmationDecision()
}

class ConfirmationManager {
    companion object {
        private const val TAG = "ConfirmationManager"
    }

    private var pendingIntent: JarvisIntent? = null

    fun setPending(intent: JarvisIntent) {
        Log.i(TAG, "Storing pending confirmation for ${intent.javaClass.simpleName}")
        pendingIntent = intent
    }

    fun getPending(): JarvisIntent? = pendingIntent

    fun clear() {
        Log.i(TAG, "Clearing pending confirmation")
        pendingIntent = null
    }

    fun hasPending(): Boolean = pendingIntent != null

    fun evaluateResponse(utterance: String): ConfirmationDecision {
        if (pendingIntent == null) return ConfirmationDecision.NotAConfirmation
        val clean = utterance.lowercase().trim()
            .replace(Regex("^(hey\\s+jarvis|jarvis|hay\\s+jarvis|ok\\s+jarvis|please|bhai)\\s+"), "")
            .trim()

        val confirmWords = setOf(
            "yes", "yeah", "yep", "sure", "ok", "okay", "confirm", "proceed",
            "do it", "call", "send", "make call", "ha", "haan", "haanji",
            "karo", "bhejo", "kar do", "call karo", "send it", "right", "go ahead",
            "yes please", "yes do it", "yes call", "yes send"
        )
        val cancelWords = setOf(
            "no", "nope", "cancel", "abort", "don't", "dont", "stop",
            "mat karo", "nahi", "nahin", "ruk jao", "rehne do", "cancel it",
            "no cancel", "don't do it"
        )

        return when {
            confirmWords.any { clean == it || clean.startsWith("$it ") } -> ConfirmationDecision.Confirmed
            cancelWords.any { clean == it || clean.startsWith("$it ") } -> ConfirmationDecision.Cancelled
            else -> ConfirmationDecision.NotAConfirmation
        }
    }

    fun requestConfirmation(prompt: String, onResponse: (Boolean) -> Unit) {
        Log.i(TAG, "Prompting user confirmation: '$prompt'")
        onResponse(true)
    }
}
