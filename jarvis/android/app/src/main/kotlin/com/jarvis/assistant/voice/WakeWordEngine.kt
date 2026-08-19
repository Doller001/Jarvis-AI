package com.jarvis.assistant.voice

import android.util.Log

class WakeWordEngine(
    val primaryPhrase: String = "Jarvis",
    val phraseVariants: List<String> = listOf(
        "Jarvis",
        "Hey Jarvis",
        "Hay Jarvis",
        "Hey, Jarvis",
        "Jarvis hello",
        "Jarvis suno",
        "Jarvis listen",
        "Jarvis listen to me"
    ),
    var sensitivityThreshold: Float = 0.85f,
    var cooldownMs: Long = 1500L
) {
    private var isMonitoring = false
    private var lastWakeTimeMs = 0L

    fun startMonitoring(onWake: (String) -> Unit) {
        isMonitoring = true
        Log.i("WakeWordEngine", "Jarvis local wake-word engine monitoring active (Phrases: $phraseVariants)")
    }

    fun isWakePhraseMatch(text: String): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastWakeTimeMs < cooldownMs) {
            return false // False-positive cooldown suppression
        }

        val cleaned = text.lowercase().strip()
        for (variant in phraseVariants) {
            if (cleaned.contains(variant.lowercase())) {
                lastWakeTimeMs = now
                return true
            }
        }
        return false
    }

    fun stopMonitoring() {
        isMonitoring = false
        Log.i("WakeWordEngine", "Jarvis wake-word engine stopped.")
    }
}
