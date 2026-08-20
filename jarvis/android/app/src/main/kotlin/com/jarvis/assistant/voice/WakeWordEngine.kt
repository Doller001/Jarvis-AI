package com.jarvis.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer as AndroidSpeechRecognizer
import android.util.Log
import java.util.Locale

class WakeWordEngine(
    val primaryPhrase: String = "Jarvis",
    val phraseVariants: List<String> = listOf(
        "Jarvis",
        "Hey Jarvis",
        "Hay Jarvis",
        "Hey, Jarvis",
        "Jarvis hello",
        "Jarvis suno",
        "Jarvis listen",
        "Jarvis listen to me"
    ),
    var sensitivityThreshold: Float = 0.85f,
    var cooldownMs: Long = 1500L,
    private val context: Context? = null
) {
    private var isMonitoring = false
    private var lastWakeTimeMs = 0L
    private var speechRecognizer: AndroidSpeechRecognizer? = null
    private var onWakeCallback: ((String) -> Unit)? = null

    fun startMonitoring(onWake: (String) -> Unit) {
        onWakeCallback = onWake
        isMonitoring = true
        Log.i("WakeWordEngine", "Jarvis wake-word engine monitoring active (Phrases: $phraseVariants)")
        listenOnce()
    }

    private fun listenOnce() {
        val ctx = context ?: run {
            Log.w("WakeWordEngine", "Context not available — cannot listen for wake word")
            return
        }
        if (!isMonitoring) return
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
                    Log.e("WakeWordEngine", "Speech recognition error code: $error")
                    if (error == AndroidSpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        isMonitoring = false
                        return
                    }
                    if (isMonitoring) listenOnce()
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(AndroidSpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    if (text.isNotBlank()) {
                        Log.i("WakeWordEngine", "Heard: '$text'")
                        if (isWakePhraseMatch(text)) {
                            onWakeCallback?.invoke(text)
                        }
                    }
                    if (isMonitoring) listenOnce()
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e("WakeWordEngine", "Failed to start speech recognizer", e)
        }
    }

    fun isWakePhraseMatch(text: String): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastWakeTimeMs < cooldownMs) {
            return false // False-positive cooldown suppression
        }

        val cleaned = text.lowercase().strip()
        for (variant in phraseVariants) {
            if (cleaned.contains(variant.lowercase())) {
                lastWakeTimeMs = now
                return true
            }
        }
        return false
    }

    fun extractCommand(fullText: String): String {
        var command = fullText.lowercase()
        for (variant in phraseVariants) {
            command = command.replace(variant.lowercase(), " ")
        }
        return command.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    fun stopMonitoring() {
        isMonitoring = false
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e("WakeWordEngine", "Failed to stop recognizer", e)
        }
        speechRecognizer = null
        Log.i("WakeWordEngine", "Jarvis wake-word engine stopped.")
    }
}