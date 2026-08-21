package com.jarvis.assistant.brain

class ResponseGenerator {
    fun generateSpokenResponse(intent: JarvisIntent, resultMessage: String): String {
        return when (intent) {
            is JarvisIntent.ToggleTorch -> "Flashlight turned ${intent.state}."
            is JarvisIntent.ToggleWifi -> "Wi-Fi turned ${intent.state}."
            is JarvisIntent.ToggleBluetooth -> "Bluetooth turned ${intent.state}."
            is JarvisIntent.GetTime -> resultMessage
            is JarvisIntent.GetBattery -> resultMessage
            is JarvisIntent.SetVolume -> resultMessage
            is JarvisIntent.MediaControl -> resultMessage
            is JarvisIntent.OpenApp -> "Opening ${intent.appName}."
            is JarvisIntent.CloseApp -> resultMessage
            is JarvisIntent.SendWhatsApp -> "Sending WhatsApp message to ${intent.contactName}."
            else -> resultMessage
        }
    }
}
