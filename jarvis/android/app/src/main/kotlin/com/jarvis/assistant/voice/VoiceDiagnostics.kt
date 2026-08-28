package com.jarvis.assistant.voice

import android.speech.SpeechRecognizer as AndroidSpeechRecognizer
import android.util.Log

/**
 * Diagnostic logger and error translator for the Jarvis voice pipeline.
 * Includes session generation tracking and latency measurements.
 */
object VoiceDiagnostics {
    private const val TAG = "VoiceDiagnostics"

    fun logStart(language: String, preferOffline: Boolean) {
        Log.i(TAG, "[VOICE_DIAG] START: SpeechRecognizer init (lang=$language, preferOffline=$preferOffline)")
    }

    fun logReady() {
        Log.i(TAG, "[VOICE_DIAG] READY: Microphone acquired and ready for speech input")
    }

    fun logRms(rmsdB: Float) {
        // Per-frame RMS logs suppressed to eliminate I/O and CPU overhead
    }

    fun logBegin() {
        Log.i(TAG, "[VOICE_DIAG] BEGIN: User speech detected — stream active")
    }

    fun logEnd() {
        Log.i(TAG, "[VOICE_DIAG] END: User finished speaking — finalizing buffer")
    }

    fun logPartialResult(text: String) {
        Log.d(TAG, "[VOICE_DIAG] PARTIAL: '$text'")
    }

    fun logResult(text: String) {
        Log.i(TAG, "[VOICE_DIAG] RESULT: '$text'")
    }

    fun logError(errorCode: Int) {
        val (name, message) = getErrorDetails(errorCode)
        Log.w(TAG, "[VOICE_DIAG] ERROR (Code $errorCode / $name): $message")
    }

    fun logMicState(state: String) {
        Log.i(TAG, "[VOICE_DIAG] MIC_STATE: $state")
    }

    fun logSessionGeneration(generation: Long) {
        Log.i(TAG, "[VOICE_DIAG] SESSION_GEN: $generation")
    }

    fun logWakeCandidate(
        score: Float,
        threshold: Float,
        positiveHits: Int,
        windowSize: Int,
        rms: Float,
        noiseFloor: Float,
        decision: String,
        rejectReason: String?
    ) {
        val msg = buildString {
            append("[WAKE] ")
            append("score=${"%.3f".format(score)} ")
            append("threshold=${"%.3f".format(threshold)} ")
            append("hits=$positiveHits/$windowSize ")
            append("rms=${"%.3f".format(rms)} ")
            append("noise=${"%.3f".format(noiseFloor)} ")
            append("decision=$decision")
            if (rejectReason != null) append(" reason=$rejectReason")
        }
        if (decision == "ACCEPT") {
            Log.i(TAG, "[VOICE_DIAG] $msg")
        } else {
            Log.v(TAG, "[VOICE_DIAG] $msg")
        }
    }

    // Latency measurement methods
    fun logWakeDetectionLatency(latencyMs: Long) {
        Log.i(TAG, "[VOICE_DIAG] LATENCY wake_detection=${latencyMs}ms")
    }

    fun logMicHandoffLatency(latencyMs: Long) {
        Log.i(TAG, "[VOICE_DIAG] LATENCY mic_handoff=${latencyMs}ms")
    }

    fun logSttStartupLatency(latencyMs: Long) {
        Log.i(TAG, "[VOICE_DIAG] LATENCY stt_startup=${latencyMs}ms")
    }

    fun logWakeToSttLatency(latencyMs: Long) {
        Log.i(TAG, "[VOICE_DIAG] LATENCY wake_to_stt=${latencyMs}ms")
    }

    fun logInterruptDetectionLatency(latencyMs: Long) {
        Log.i(TAG, "[VOICE_DIAG] LATENCY interrupt_detection=${latencyMs}ms")
    }

    fun logInterruptToTtsStopLatency(latencyMs: Long) {
        Log.i(TAG, "[VOICE_DIAG] LATENCY interrupt_to_tts_stop=${latencyMs}ms")
    }

    // Lifecycle events
    fun logLifecycleEvent(event: String) {
        Log.i(TAG, "[VOICE_DIAG] LIFECYCLE: $event")
    }

    fun getErrorDetails(errorCode: Int): Pair<String, String> {
        return when (errorCode) {
            AndroidSpeechRecognizer.ERROR_AUDIO ->
                "ERROR_AUDIO" to "Audio recording error. Check microphone hardware."
            AndroidSpeechRecognizer.ERROR_CLIENT ->
                "ERROR_CLIENT" to "Client-side error in speech recognition service."
            AndroidSpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "ERROR_INSUFFICIENT_PERMISSIONS" to "Microphone permission (RECORD_AUDIO) required."
            AndroidSpeechRecognizer.ERROR_NETWORK ->
                "ERROR_NETWORK" to "Network error during cloud speech recognition."
            AndroidSpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "ERROR_NETWORK_TIMEOUT" to "Network operation timed out."
            AndroidSpeechRecognizer.ERROR_NO_MATCH ->
                "ERROR_NO_MATCH" to "No speech matched (silence or low volume)."
            AndroidSpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                "ERROR_RECOGNIZER_BUSY" to "SpeechRecognizer busy. Resetting session."
            AndroidSpeechRecognizer.ERROR_SERVER ->
                "ERROR_SERVER" to "Speech recognition backend server error."
            AndroidSpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                "ERROR_SPEECH_TIMEOUT" to "No speech detected within timeout."
            AndroidSpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
                "ERROR_LANGUAGE_NOT_SUPPORTED" to "Speech language not supported."
            AndroidSpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                "ERROR_LANGUAGE_UNAVAILABLE" to "Speech language currently unavailable."
            AndroidSpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
                "ERROR_SERVER_DISCONNECTED" to "Server disconnected during recognition."
            AndroidSpeechRecognizer.ERROR_TOO_MANY_REQUESTS ->
                "ERROR_TOO_MANY_REQUESTS" to "Too many requests to speech recognition service."
            else ->
                "ERROR_UNKNOWN_$errorCode" to "Unknown error code $errorCode."
        }
    }
}
