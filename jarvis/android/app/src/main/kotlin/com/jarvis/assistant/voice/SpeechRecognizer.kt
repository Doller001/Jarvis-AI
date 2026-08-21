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
 * Command-mode speech recognizer. Runs when listening for user commands.
 * Captures speech continuously, handles Bluetooth / Device audio focus,
 * recovers partial utterances on early silence, and supports multi-language.
 */
class SpeechRecognizer(private val context: Context? = null) {

    companion object {
        private const val TAG = "SpeechRecognizer"
    }

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
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .build()
                audioFocusRequest = request
                am.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
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

    fun startListening(onResult: (String) -> Unit, onError: (Int) -> Unit) {
        val ctx = context ?: run {
            Log.w(TAG, "Context not available for native STT")
            onError(AndroidSpeechRecognizer.ERROR_CLIENT)
            return
        }

        if (!AndroidSpeechRecognizer.isRecognitionAvailable(ctx)) {
            Log.e(TAG, "Speech recognition is not available on this device!")
            onError(AndroidSpeechRecognizer.ERROR_CLIENT)
            return
        }

        mainHandler.post {
            try {
                isListening = true
                lastRecognizedText = ""

                requestAudioFocus()

                // Cancel previous session if any to avoid busy/stuck states
                try {
                    speechRecognizer?.cancel()
                    speechRecognizer?.destroy()
                } catch (_: Exception) {}

                speechRecognizer = AndroidSpeechRecognizer.createSpeechRecognizer(ctx)

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
                }

                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(TAG, "Ready for speech — microphone active")
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(TAG, "User started speaking")
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        Log.d(TAG, "User finished speaking — processing")
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        abandonAudioFocus()
                        Log.w(TAG, "Speech recognition code: $error")

                        // If partial results captured, deliver them instead of discarding
                        if (lastRecognizedText.isNotBlank() && (
                                error == AndroidSpeechRecognizer.ERROR_NO_MATCH ||
                                error == AndroidSpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                                error == AndroidSpeechRecognizer.ERROR_CLIENT
                            )) {
                            Log.i(TAG, "Recovered utterance from captured speech: '$lastRecognizedText'")
                            val text = lastRecognizedText
                            lastRecognizedText = ""
                            onResult(text)
                        } else {
                            onError(error)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        abandonAudioFocus()
                        val matches = results?.getStringArrayList(AndroidSpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull { it.isNotBlank() } ?: lastRecognizedText
                        lastRecognizedText = ""

                        if (text.isNotBlank()) {
                            Log.i(TAG, "Speech recognized: '$text'")
                            onResult(text)
                        } else {
                            Log.w(TAG, "No speech recognized")
                            onError(AndroidSpeechRecognizer.ERROR_NO_MATCH)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(AndroidSpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull { it.isNotBlank() }
                        if (!text.isNullOrBlank()) {
                            lastRecognizedText = text
                            Log.d(TAG, "Partial speech: '$text'")
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                speechRecognizer?.startListening(intent)
                Log.i(TAG, "STT engine started listening")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start speech recognizer", e)
                isListening = false
                abandonAudioFocus()
                onError(AndroidSpeechRecognizer.ERROR_CLIENT)
            }
        }
    }

    fun stopListening() {
        isListening = false
        abandonAudioFocus()
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop speech recognizer", e)
            }
        }
    }

    fun destroy() {
        isListening = false
        abandonAudioFocus()
        lastRecognizedText = ""
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to destroy speech recognizer", e)
            }
            speechRecognizer = null
        }
    }
}