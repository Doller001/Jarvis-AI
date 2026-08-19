package com.jarvis.assistant.brain

class ResponseGenerator {
    fun generateSpokenResponse(intent: JarvisIntent, resultMessage: String): String {
        return when (intent) {
            is JarvisIntent.ToggleTorch -> "Jarvis turned flashlight ${intent.state}."
            is JarvisIntent.ToggleWifi -> "Jarvis set Wi-Fi ${intent.state}."
            is JarvisIntent.GetTime -> "Current time is 18:15."
            is JarvisIntent.OpenApp -> "Opening ${intent.appName}."
            else -> resultMessage
        }
    }
}
