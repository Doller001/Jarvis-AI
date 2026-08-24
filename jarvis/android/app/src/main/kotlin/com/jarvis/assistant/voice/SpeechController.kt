package com.jarvis.assistant.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer as AndroidSpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * High-reliability speech recognition controller.
 * Enforces Single Mic Owner architecture, provides full callback diagnostics,
 * and manages audio focus and recognition lifecycle with on-device fallback.
 */
class SpeechController(
    private val context: Context? = null,
    private val micController: MicController = MicController(context)
) {
    companion object {
        private const val TAG = "SpeechController"
        private const val OWNER_TAG = "SpeechController"
    }

    @Volatile
    private var isListening = false
    @Volatile
    private var errorDelivered = false
    private var speechRecognizer: AndroidSpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastRecognizedText = ""
    private val audioManager: AudioManager? = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    // NOTE: Audio focus is intentionally NOT requested here. On several OEM
    // ROMs (notably Samsung OneUI) acquiring AUDIOFOCUS with a speech/guidance
    // usage silently mutes or re-routes the capture stream, so the
    // SpeechRecognizer receives ~0 audio energy and always returns
    // ERROR_NO_MATCH. The standard Android recognizer manages its own audio
    // session; leaving focus alone makes listening work out of the box.
    private fun requestAudioFocus() {
        // No-op by design (see note above).
    }

    private fun abandonAudioFocus() {
        try {
            val am = audioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        } catch (_: Exception) {}
    }

    /**
     * Starts listening with strict permission, availability, and mic-ownership gating.
     */
    fun startListening(
        onResult: (String) -> Unit,
        onError: (errorCode: Int, errorMessage: String) -> Unit,
        onRmsChanged: ((Float) -> Unit)? = null
    ) {
        val ctx = context ?: run {
            Log.w(TAG, "Context is null for SpeechController")
            onError(AndroidSpeechRecognizer.ERROR_CLIENT, "Application context is null.")
            return
        }

        // 1. Permission Gate
        if (!micController.hasPermission()) {
            VoiceDiagnostics.logError(AndroidSpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
            onError(
                AndroidSpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
                "Microphone permission (RECORD_AUDIO) has not been granted."
            )
            return
        }

        // 2. Recognition Availability Gate (check standard or on-device)
        val isStandardAvailable = AndroidSpeechRecognizer.isRecognitionAvailable(ctx)
        val isOnDeviceAvailable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AndroidSpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)
        } else {
            false
        }

        if (!isStandardAvailable && !isOnDeviceAvailable) {
            VoiceDiagnostics.logError(AndroidSpeechRecognizer.ERROR_CLIENT)
            onError(
                AndroidSpeechRecognizer.ERROR_CLIENT,
                "Speech Recognition service is not available on this device."
            )
            return
        }

        // 3. Mic Ownership Gate (Single Mic Owner Architecture)
        micController.forceAcquire(OWNER_TAG)

        mainHandler.post {
            try {
                isListening = true
                errorDelivered = false
                lastRecognizedText = ""

                requestAudioFocus()

                // Reset previous recognizer instance if active
                try {
                    speechRecognizer?.cancel()
                    speechRecognizer?.destroy()
                } catch (_: Exception) {}

                // Always use the standard (cloud/Google) SpeechRecognizer.
                // On several OEM ROMs (notably Samsung OneUI / Android 13) the
                // on-device recognizer reports isOnDeviceRecognitionAvailable()
                // == true but the actual model is NOT installed, so it captures
                // zero audio and always returns ERROR_NO_MATCH. The standard
                // recognizer is the reliable path that actually delivers audio.
                speechRecognizer = AndroidSpeechRecognizer.createSpeechRecognizer(ctx)

                val defaultLocale = Locale.getDefault()
                val langTag = if (defaultLocale.language.isNotBlank()) defaultLocale.toLanguageTag() else "en-US"
                VoiceDiagnostics.logStart(langTag, preferOffline = false)

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN", "hi-IN", "en-US"))
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    // Generous speech input thresholds to prevent premature silence cutoffs
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                }

                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        VoiceDiagnostics.logReady()
                    }

                    override fun onBeginningOfSpeech() {
                        VoiceDiagnostics.logBegin()
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        VoiceDiagnostics.logRms(rmsdB)
                        onRmsChanged?.invoke(rmsdB)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        VoiceDiagnostics.logEnd()
                    }

                    override fun onError(error: Int) {
                    // Guard against duplicate delivery: a subsequent
                    // cancel()/destroy() after an already-reported error can
                    // fire ERROR_CLIENT again — suppress it.
                    if (errorDelivered) {
                        Log.d(TAG, "onError($error) ignored — already delivered")
                        return
                    }
                    isListening = false
                    errorDelivered = true
                    abandonAudioFocus()
                    micController.releaseMic(OWNER_TAG)
                    VoiceDiagnostics.logError(error)

                    val (_, detailedMessage) = VoiceDiagnostics.getErrorDetails(error)

                    // If user spoke and partial results were captured before error/silence, recover it
                    if (lastRecognizedText.isNotBlank() && (
                            error == AndroidSpeechRecognizer.ERROR_NO_MATCH ||
                            error == AndroidSpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                            error == AndroidSpeechRecognizer.ERROR_CLIENT
                        )) {
                        VoiceDiagnostics.logResult("$lastRecognizedText (recovered from partial)")
                        val text = lastRecognizedText
                        lastRecognizedText = ""
                        onResult(text)
                    } else {
                        onError(error, detailedMessage)
                    }
                }

                override fun onResults(results: Bundle?) {
                    if (errorDelivered) return
                    isListening = false
                    errorDelivered = true
                    abandonAudioFocus()
                    micController.releaseMic(OWNER_TAG)

                        val matches = results?.getStringArrayList(AndroidSpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull { it.isNotBlank() } ?: lastRecognizedText
                        lastRecognizedText = ""

                        if (text.isNotBlank()) {
                            VoiceDiagnostics.logResult(text)
                            onResult(text)
                        } else {
                            VoiceDiagnostics.logError(AndroidSpeechRecognizer.ERROR_NO_MATCH)
                            val (_, msg) = VoiceDiagnostics.getErrorDetails(AndroidSpeechRecognizer.ERROR_NO_MATCH)
                            onError(AndroidSpeechRecognizer.ERROR_NO_MATCH, msg)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(AndroidSpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull { it.isNotBlank() }
                        if (!text.isNullOrBlank()) {
                            lastRecognizedText = text
                            VoiceDiagnostics.logPartialResult(text)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Exception starting SpeechRecognizer", e)
                isListening = false
                abandonAudioFocus()
                micController.releaseMic(OWNER_TAG)
                VoiceDiagnostics.logError(AndroidSpeechRecognizer.ERROR_CLIENT)
                onError(AndroidSpeechRecognizer.ERROR_CLIENT, e.message ?: "Failed to start speech recognizer.")
            }
        }
    }

    fun stopListening() {
        isListening = false
        abandonAudioFocus()
        micController.releaseMic(OWNER_TAG)
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to stop SpeechRecognizer", e)
            }
        }
    }

    fun destroy() {
        isListening = false
        abandonAudioFocus()
        micController.releaseMic(OWNER_TAG)
        lastRecognizedText = ""
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to destroy SpeechRecognizer", e)
            }
            speechRecognizer = null
        }
    }
}
