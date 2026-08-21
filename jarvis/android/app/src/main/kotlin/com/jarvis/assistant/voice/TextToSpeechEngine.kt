package com.jarvis.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class TextToSpeechEngine(private val context: Context? = null) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        context?.let { ctx ->
            tts = TextToSpeech(ctx) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale.US)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.w("TextToSpeechEngine", "Language US is not supported or missing data")
                    }
                    isInitialized = true
                    Log.i("TextToSpeechEngine", "TTS initialized successfully")
                } else {
                    Log.e("TextToSpeechEngine", "TTS initialization failed with status $status")
                }
            }
        }
    }

    fun speak(text: String, onComplete: () -> Unit = {}) {
        if (text.isBlank()) {
            onComplete()
            return
        }
        Log.i("TextToSpeechEngine", "Jarvis speaking: '$text'")
        val ttsEngine = tts
        if (ttsEngine != null && isInitialized) {
            val utteranceId = "JARVIS_TTS_${System.currentTimeMillis()}"
            ttsEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(uttId: String?) {}
                override fun onDone(uttId: String?) {
                    if (uttId == utteranceId) onComplete()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(uttId: String?) {
                    if (uttId == utteranceId) onComplete()
                }
            })
            ttsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } else {
            // Fallback if TTS not initialized or context missing
            onComplete()
        }
    }

    fun setSpeechRate(rate: Float) {
        try {
            tts?.setSpeechRate(rate)
        } catch (e: Exception) {
            Log.e("TextToSpeechEngine", "Error setting speech rate", e)
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e("TextToSpeechEngine", "Error stopping TTS", e)
        }
    }

    fun shutdown() {
        try {
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (e: Exception) {
            Log.e("TextToSpeechEngine", "Error shutting down TTS", e)
        }
    }
}
