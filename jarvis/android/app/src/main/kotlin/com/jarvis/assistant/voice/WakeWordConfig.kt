package com.jarvis.assistant.voice

/**
 * Central wake-word configuration. Safe defaults; tune sensitivity/cooldown
 * from real-device testing.
 */
data class WakeWordConfig(
    val enabled: Boolean = true,
    /** 0.0 (least sensitive) .. 1.0 (most sensitive). */
    val sensitivity: Float = 0.8f,
    /** Minimum gap between wake events. */
    val cooldownMs: Long = 1500,
    /** Max time the command recognizer listens before giving up. */
    val commandTimeoutMs: Long = 8000,
    /** Gap after TTS finishes before the wake detector resumes. */
    val ttsCooldownMs: Long = 500,
    /** Keep listening while the app is backgrounded. */
    val backgroundListeningEnabled: Boolean = true,
    /** Fall back to continuous STT text matching when no offline detector is available. */
    val fallbackTextMatchingEnabled: Boolean = true
)