package com.jarvis.assistant.voice

import android.content.Context
import android.content.Intent
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
 * Listens continuously until the user stops speaking, capturing partial
 * and final recognition results, and recovers speech even if the underlying
 * engine signals timeout after utterance completion.
 */
class SpeechRecognizer(private val context: Context? = null) {
    private var isListening = false
    private var speechRecognizer: AndroidSpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastRecognizedText = ""

    fun startListening(onResult: (String) -> Unit, onError: (Int) -> Unit) {
        val ctx = context ?: run {
            Log.w("SpeechRecognizer", "Context not available for native STT")
            onError(AndroidSpeechRecognizer.ERROR_CLIENT)
            return
        }

        mainHandler.post {
            try {
                isListening = true
                lastRecognizedText = ""

                // Cancel previous session if any to avoid busy/stuck states
                try {
                    speechRecognizer?.cancel()
                    speechRecognizer?.destroy()
                } catch (_: Exception) {}

                speechRecognizer = AndroidSpeechRecognizer.createSpeechRecognizer(ctx)

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    // Keep listening until the user stops speaking
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
                }

                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d("SpeechRecognizer", "Ready for speech — listening...")
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d("SpeechRecognizer", "User began speaking")
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        Log.d("SpeechRecognizer", "User stopped speaking — finalizing")
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        Log.e("SpeechRecognizer", "Speech recognition error code: $error")

                        // If user spoke and partial results were captured, recover them instead of failing
                        if (lastRecognizedText.isNotBlank() && (
                                error == AndroidSpeechRecognizer.ERROR_NO_MATCH ||
                                error == AndroidSpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                                error == AndroidSpeechRecognizer.ERROR_CLIENT
                            )) {
                            Log.i("SpeechRecognizer", "Recovered utterance from partial results: '$lastRecognizedText'")
                            val text = lastRecognizedText
                            lastRecognizedText = ""
                            onResult(text)
                        } else {
                            onError(error)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches = results?.getStringArrayList(AndroidSpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull { it.isNotBlank() } ?: lastRecognizedText
                        lastRecognizedText = ""

                        if (text.isNotBlank()) {
                            Log.i("SpeechRecognizer", "Command received: '$text'")
                            onResult(text)
                        } else {
                            Log.w("SpeechRecognizer", "No speech matched")
                            onError(AndroidSpeechRecognizer.ERROR_NO_MATCH)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(AndroidSpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull { it.isNotBlank() }
                        if (!text.isNullOrBlank()) {
                            lastRecognizedText = text
                            Log.d("SpeechRecognizer", "Partial speech captured: '$text'")
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                speechRecognizer?.startListening(intent)
                Log.i("SpeechRecognizer", "STT engine listening started")
            } catch (e: Exception) {
                Log.e("SpeechRecognizer", "Failed to start speech recognizer", e)
                isListening = false
                onError(AndroidSpeechRecognizer.ERROR_CLIENT)
            }
        }
    }

    fun stopListening() {
        isListening = false
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.e("SpeechRecognizer", "Failed to stop speech recognizer", e)
            }
        }
    }

    /**
     * Destroy is deferred to the main thread: destroying a recognizer from
     * inside its own callback is undefined behaviour on some OEM builds.
     */
    fun destroy() {
        isListening = false
        lastRecognizedText = ""
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.e("SpeechRecognizer", "Failed to destroy speech recognizer", e)
            }
            speechRecognizer = null
        }
    }
}