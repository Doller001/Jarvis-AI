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

enum class TriggerReason {
    WakeWordConfirmed,
    ManualButton,
    BargeInInterrupt
}

data class CommandListeningRequest(
    val reason: TriggerReason,
    val sessionId: Long
)

/**
 * High-reliability speech recognition controller.
 * Enforces Single Mic Owner architecture, strict TriggerReason gating,
 * session generation tracking, and clean teardown.
 */
class SpeechController(
    private val context: Context? = null,
    private val micController: MicController = MicController(context)
) {
    companion object {
        private const val TAG = "SpeechController"
        const val OWNER_TAG = MicController.OWNER_STT
    }

    @Volatile
    private var isListening = false
    @Volatile
    private var errorDelivered = false
    @Volatile
    private var activeSessionId: Long = 0L

    private var speechRecognizer: AndroidSpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastRecognizedText = ""
    private val audioManager: AudioManager? = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private fun requestAudioFocus() {
        // Managed by platform SpeechRecognizer
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
     * Starts listening with strict permission, availability, trigger validation, and mic-ownership gating.
     */
    fun startListening(
        request: CommandListeningRequest,
        onResult: (String) -> Unit,
        onError: (errorCode: Int, errorMessage: String) -> Unit,
        onRmsChanged: ((Float) -> Unit)? = null
    ) {
        val ctx = context ?: run {
            Log.w(TAG, "Context is null for SpeechController")
            onError(AndroidSpeechRecognizer.ERROR_CLIENT, "Application context is null.")
            return
        }

        activeSessionId = request.sessionId
        Log.i(TAG, "Speech listening requested: reason=${request.reason}, sessionId=${request.sessionId}")

        // 1. Permission Gate
        if (!micController.hasPermission()) {
            VoiceDiagnostics.logError(AndroidSpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
            onError(
                AndroidSpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
                "Microphone permission (RECORD_AUDIO) has not been granted."
            )
            return
        }

        // 2. Recognition Availability Gate
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
            val currentOwner = micController.getCurrentOwner()
            Log.w(TAG, "Cannot acquire mic for STT — currently held by $currentOwner")
            onError(
                AndroidSpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                "Microphone busy (held by $currentOwner)"
            )
            return
        }

        mainHandler.post {
            try {
                if (activeSessionId != request.sessionId) {
                    Log.w(TAG, "Session superseded before recognition start (${request.sessionId} vs $activeSessionId)")
                    micController.releaseMic(OWNER_TAG)
                    return@post
                }

                isListening = true
                errorDelivered = false
                lastRecognizedText = ""

                requestAudioFocus()

                // Reset previous recognizer instance if active
                try {
                    speechRecognizer?.cancel()
                    speechRecognizer?.destroy()
                } catch (_: Exception) {}

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
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
                }

                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        if (activeSessionId == request.sessionId) VoiceDiagnostics.logReady()
                    }

                    override fun onBeginningOfSpeech() {
                        if (activeSessionId == request.sessionId) VoiceDiagnostics.logBegin()
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        if (activeSessionId == request.sessionId) {
                            VoiceDiagnostics.logRms(rmsdB)
                            onRmsChanged?.invoke(rmsdB)
                        }
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        if (activeSessionId == request.sessionId) VoiceDiagnostics.logEnd()
                    }

                    override fun onError(error: Int) {
                        if (activeSessionId != request.sessionId) {
                            Log.d(TAG, "Discarding stale STT onError($error) for session ${request.sessionId}")
                            return
                        }
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

                    // If user spoke and partial results were captured before error/silence/network glitch, recover it
                    if (lastRecognizedText.isNotBlank() && (
                            error == AndroidSpeechRecognizer.ERROR_NO_MATCH ||
                            error == AndroidSpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                            error == AndroidSpeechRecognizer.ERROR_CLIENT ||
                            error == AndroidSpeechRecognizer.ERROR_NETWORK ||
                            error == AndroidSpeechRecognizer.ERROR_NETWORK_TIMEOUT
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
        val wasListening = isListening
        val recognizer = speechRecognizer
        // Detach first, so overlapping teardown paths cannot cancel or destroy
        // the same SpeechRecognizer twice.
        speechRecognizer = null
        isListening = false
        abandonAudioFocus()
        if (micController.isOwnedBy(OWNER_TAG)) {
            micController.releaseMic(OWNER_TAG)
        }
        lastRecognizedText = ""
        if (recognizer == null) return
        mainHandler.post {
            try {
                // Calling cancel after the platform has delivered onError or
                // onResults produces Samsung's "not connected" error. Only
                // cancel an active recognition session; destroy always frees
                // the recognizer instance.
                if (wasListening) recognizer.cancel()
                recognizer.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to destroy SpeechRecognizer", e)
            }
        }
    }
}
