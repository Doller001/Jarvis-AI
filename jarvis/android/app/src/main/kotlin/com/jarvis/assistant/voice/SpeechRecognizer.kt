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
 * Command-mode speech recognizer. Runs ONLY after the wake word has been
 * detected — never continuously. Reports errors to the caller so the voice
 * runtime can recover instead of silently dying.
 */
class SpeechRecognizer(private val context: Context? = null) {
    private var isListening = false
    private var speechRecognizer: AndroidSpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun startListening(onResult: (String) -> Unit, onError: (Int) -> Unit) {
        isListening = true
        Log.d("SpeechRecognizer", "STT engine listening...")
        val ctx = context ?: run {
            Log.w("SpeechRecognizer", "Context not available for native STT")
            onError(AndroidSpeechRecognizer.ERROR_CLIENT)
            return
        }

        try {
            if (speechRecognizer == null) {
                speechRecognizer = AndroidSpeechRecognizer.createSpeechRecognizer(ctx)
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    isListening = false
                    Log.e("SpeechRecognizer", "Speech recognition error code: $error")
                    onError(error)
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(AndroidSpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    if (text.isNotBlank()) {
                        Log.i("SpeechRecognizer", "Command received")
                        onResult(text)
                    } else {
                        onError(AndroidSpeechRecognizer.ERROR_NO_MATCH)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e("SpeechRecognizer", "Failed to start speech recognizer", e)
            isListening = false
            onError(AndroidSpeechRecognizer.ERROR_CLIENT)
        }
    }

    fun stopListening() {
        isListening = false
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e("SpeechRecognizer", "Failed to stop speech recognizer", e)
        }
    }

    /**
     * Destroy is deferred to the main thread: destroying a recognizer from
     * inside its own callback is undefined behaviour on some OEM builds.
     */
    fun destroy() {
        isListening = false
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.e("SpeechRecognizer", "Failed to destroy speech recognizer", e)
            }
            speechRecognizer = null
        }
    }
}