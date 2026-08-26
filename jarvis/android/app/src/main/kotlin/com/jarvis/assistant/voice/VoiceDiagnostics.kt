package com.jarvis.assistant.voice

import android.speech.SpeechRecognizer as AndroidSpeechRecognizer
import android.util.Log

/**
 * Diagnostic logger and error translator for the Jarvis voice pipeline.
 * Phase 11: Added forensic wake-candidate logging with full decision context.
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
        // Suppress per-frame RMS logs to eliminate I/O and CPU overhead
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

    /**
     * Phase 11: Forensic wake-candidate log.
     * Emitted for EVERY inference window so post-mortem analysis can explain
     * exactly why "Yes Boss" did or did not happen.
     *
     * Example ACCEPT:
     *   [WAKE] score=0.78 threshold=0.60 hits=3/5 rms=0.042 noise=0.012 decision=ACCEPT
     *
     * Example REJECT:
     *   [WAKE] score=0.43 threshold=0.60 hits=1/5 rms=0.035 noise=0.012
     *          decision=REJECT reason=TEMPORAL_GATE(hits=1/3)
     */
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
