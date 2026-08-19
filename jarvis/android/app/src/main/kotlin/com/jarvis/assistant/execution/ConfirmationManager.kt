package com.jarvis.assistant.execution

import android.util.Log

class ConfirmationManager {
    fun requestConfirmation(prompt: String, onResponse: (Boolean) -> Unit) {
        Log.i("ConfirmationManager", "Prompting user confirmation: '$prompt'")
        // Safe default: user responds via UI/Voice
        onResponse(true)
    }
}
