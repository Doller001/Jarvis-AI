package com.jarvis.assistant.voice.wakeword

/**
 * Central wake-word configuration.
 *
 * Phase 1 fix: fallbackTextMatchingEnabled = false by default.
 * This removes the SpeechRecognizer continuous-listening fallback.
 * When ONNX models are unavailable the engine reports an error and stops —
 * it does NOT fall back to continuous STT.
 */
data class WakeWordConfig(
    val enabled: Boolean = true,
    var sensitivity: Float = 0.8f,
    val cooldownMs: Long = 2500,        // Phase 9: raised from 1500 → 2500 ms
    val backgroundListeningEnabled: Boolean = true,
    // Phase 1 FIX: disabled by default — SpeechRecognizer must NEVER run in wake mode.
    val fallbackTextMatchingEnabled: Boolean = false,
    // Phase 4: temporal gate parameters
    val temporalWindowSize: Int = 5,    // evaluate last N inference windows
    val temporalPositiveCount: Int = 3, // require at least M positives in the window
    val minConfidenceForPositive: Float = 0.55f  // per-window minimum to count as positive
) {
    companion object {
        /** Maps sensitivity (0..1) to a classifier detection threshold. */
        fun thresholdForSensitivity(sensitivity: Float): Float {
            val s = sensitivity.coerceIn(0f, 1f)
            // sensitivity 1.0 (eager) → 0.45; 0.8 (balanced) → 0.53; 0.0 (strict) → 0.85
            return (0.85f - (0.40f * s)).coerceIn(0.40f, 0.90f)
        }
    }
}
