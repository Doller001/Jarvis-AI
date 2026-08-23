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
    private var speechRecognizer: AndroidSpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastRecognizedText = ""
    private val audioManager: AudioManager? = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private fun requestAudioFocus() {
        try {
            val am = audioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .build()
                audioFocusRequest = request
                am.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio focus request failed", e)
        }
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
        if (!micController.acquireMic(OWNER_TAG)) {
            Log.w(TAG, "Mic is currently held by: ${micController.getCurrentOwner()}")
            onError(
                AndroidSpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                "Microphone is currently held by another component (${micController.getCurrentOwner()})."
            )
            return
        }

        mainHandler.post {
            try {
                isListening = true
                lastRecognizedText = ""

                requestAudioFocus()

                // Reset previous recognizer instance if active
                try {
                    speechRecognizer?.cancel()
                    speechRecognizer?.destroy()
                } catch (_: Exception) {}

                // Prefer On-Device recognizer when available (ultra-low latency, works offline)
                speechRecognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isOnDeviceAvailable) {
                    try {
                        AndroidSpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed creating on-device recognizer, falling back to standard", e)
                        AndroidSpeechRecognizer.createSpeechRecognizer(ctx)
                    }
                } else {
                    AndroidSpeechRecognizer.createSpeechRecognizer(ctx)
                }

                val defaultLocale = Locale.getDefault()
                val langTag = if (defaultLocale.language.isNotBlank()) defaultLocale.toLanguageTag() else "en-US"
                VoiceDiagnostics.logStart(langTag, preferOffline = isOnDeviceAvailable)

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500)
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
                        isListening = false
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
                        isListening = false
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
