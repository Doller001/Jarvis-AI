package com.jarvis.assistant.voice

/**
 * Pure cooldown gate — duplicate wake events within [cooldownMs] are ignored.
 * No Android imports (JVM-testable).
 */
class WakeCooldown(private val cooldownMs: Long) {
    private var lastWakeTimeMs = 0L

    fun allow(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastWakeTimeMs < cooldownMs) return false
        lastWakeTimeMs = now
        return true
    }

    fun reset() {
        lastWakeTimeMs = 0L
    }
}