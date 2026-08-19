package com.jarvis.assistant.execution

import android.util.Log
import com.jarvis.assistant.brain.JarvisIntent

class CommandExecutor {
    fun execute(intent: JarvisIntent): String {
        Log.i("CommandExecutor", "Executing intent ${intent::class.simpleName}")
        return when (intent) {
            is JarvisIntent.ToggleTorch -> "Torch set to ${intent.state}"
            is JarvisIntent.ToggleWifi -> "Wi-Fi set to ${intent.state}"
            is JarvisIntent.GetTime -> "18:15"
            is JarvisIntent.GetBattery -> "85%"
            is JarvisIntent.OpenApp -> "App ${intent.appName} launched"
            is JarvisIntent.ReadScreen -> "Screen contents read via Accessibility"
            else -> "Executed ${intent::class.simpleName}"
        }
    }
}
