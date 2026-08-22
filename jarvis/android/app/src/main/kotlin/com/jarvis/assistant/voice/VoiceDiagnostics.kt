package com.jarvis.assistant.voice

import android.speech.SpeechRecognizer as AndroidSpeechRecognizer
import android.util.Log

/**
 * Diagnostic logger and error translator for the Jarvis voice pipeline.
 * Converts raw Android speech codes into actionable, human-readable insights.
 */
object VoiceDiagnostics {
    private const val TAG = "VoiceDiagnostics"

    fun logStart(language: String, preferOffline: Boolean) {
        Log.i(TAG, "[VOICE_DIAG] START: Initializing SpeechRecognizer (lang=$language, preferOffline=$preferOffline)")
    }

    fun logReady() {
        Log.i(TAG, "[VOICE_DIAG] READY: Microphone acquired and ready for speech input")
    }

    fun logRms(rmsdB: Float) {
        if (rmsdB > 2f) {
            Log.v(TAG, "[VOICE_DIAG] RMS: Audio level rmsDb=${"%.1f".format(rmsdB)}")
        }
    }

    fun logBegin() {
        Log.i(TAG, "[VOICE_DIAG] BEGIN: User speech detected — stream active")
    }

    fun logEnd() {
        Log.i(TAG, "[VOICE_DIAG] END: User finished speaking — finalizing audio buffer")
    }

    fun logPartialResult(text: String) {
        Log.d(TAG, "[VOICE_DIAG] PARTIAL: '$text'")
    }

    fun logResult(text: String) {
        Log.i(TAG, "[VOICE_DIAG] RESULT: Recognized utterance: '$text'")
    }

    fun logError(errorCode: Int) {
        val (name, message) = getErrorDetails(errorCode)
        Log.w(TAG, "[VOICE_DIAG] ERROR (Code $errorCode / $name): $message")
    }

    fun logMicState(state: String) {
        Log.i(TAG, "[VOICE_DIAG] MIC_STATE: $state")
    }

    fun getErrorDetails(errorCode: Int): Pair<String, String> {
        return when (errorCode) {
            AndroidSpeechRecognizer.ERROR_AUDIO ->
                "ERROR_AUDIO" to "Audio recording error. Check microphone hardware and ensure no other process holds the mic."
            AndroidSpeechRecognizer.ERROR_CLIENT ->
                "ERROR_CLIENT" to "Client-side error occurred in the speech recognition service."
            AndroidSpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "ERROR_INSUFFICIENT_PERMISSIONS" to "Insufficient permissions: RECORD_AUDIO permission is required."
            AndroidSpeechRecognizer.ERROR_NETWORK ->
                "ERROR_NETWORK" to "Network error encountered during cloud speech recognition."
            AndroidSpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "ERROR_NETWORK_TIMEOUT" to "Network operation timed out."
            AndroidSpeechRecognizer.ERROR_NO_MATCH ->
                "ERROR_NO_MATCH" to "No speech recognition result matched (silence or low volume)."
            AndroidSpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                "ERROR_RECOGNIZER_BUSY" to "SpeechRecognizer service is busy. Resetting session."
            AndroidSpeechRecognizer.ERROR_SERVER ->
                "ERROR_SERVER" to "Speech recognition backend server error."
            AndroidSpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                "ERROR_SPEECH_TIMEOUT" to "No speech input detected within the expected timeout."
            AndroidSpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
                "ERROR_LANGUAGE_NOT_SUPPORTED" to "Selected speech language is not supported on this device."
            AndroidSpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                "ERROR_LANGUAGE_UNAVAILABLE" to "Selected speech language is currently unavailable."
            AndroidSpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
                "ERROR_SERVER_DISCONNECTED" to "Server disconnected during recognition."
            AndroidSpeechRecognizer.ERROR_TOO_MANY_REQUESTS ->
                "ERROR_TOO_MANY_REQUESTS" to "Too many requests to speech recognition service."
            else ->
                "ERROR_UNKNOWN_$errorCode" to "Unknown speech recognition error code $errorCode."
        }
    }
}
