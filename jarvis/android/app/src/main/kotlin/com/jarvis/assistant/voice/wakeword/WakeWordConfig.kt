package com.jarvis.assistant.voice.wakeword

/**
 * Central wake-word configuration. Safe defaults; tune from real-device testing.
 *
 * @param enabled Master switch for offline wake-word listening.
 * @param sensitivity 0.0 (least sensitive / higher threshold) .. 1.0 (most
 *   sensitive / lower threshold). Mapped to the ONNX classifier threshold.
 * @param cooldownMs Minimum gap between two accepted wake events.
 * @param backgroundListeningEnabled Keep listening while the app is backgrounded.
 * @param fallbackTextMatchingEnabled Fall back to continuous STT text matching
 *   when the offline ONNX models are missing (degraded, battery-heavy path).
 */
data class WakeWordConfig(
    val enabled: Boolean = true,
    var sensitivity: Float = 0.8f,
    val cooldownMs: Long = 1500,
    val backgroundListeningEnabled: Boolean = true,
    val fallbackTextMatchingEnabled: Boolean = true
) {
    companion object {
        /** Maps sensitivity (0..1) to a classifier detection threshold. */
        fun thresholdForSensitivity(sensitivity: Float): Float {
            val s = sensitivity.coerceIn(0f, 1f)
            // sensitivity 1.0 -> 0.30 (eager); 0.0 -> 0.85 (strict)
            return (0.85f - (0.55f * s)).coerceIn(0.25f, 0.9f)
        }
    }
}
