package com.jarvis.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class TextToSpeechEngine(private val context: Context? = null) {
    private var tts: TextToSpeech? = null
    @Volatile
    private var isInitialized = false
    private var pendingSpeechRate: Float = 1.0f
    private val pendingQueue = java.util.concurrent.ConcurrentLinkedQueue<Pair<String, () -> Unit>>()

    init {
        context?.let { ctx ->
            tts = TextToSpeech(ctx) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale.US)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.w("TextToSpeechEngine", "Language US is not supported or missing data")
                    }
                    try {
                        val audioAttributes = android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                        tts?.setAudioAttributes(audioAttributes)
                        tts?.setSpeechRate(pendingSpeechRate)
                    } catch (e: Exception) {
                        Log.w("TextToSpeechEngine", "Could not set audio attributes on TTS", e)
                    }
                    isInitialized = true
                    Log.i("TextToSpeechEngine", "TTS initialized successfully")
                    flushPendingQueue()
                } else {
                    Log.e("TextToSpeechEngine", "TTS initialization failed with status $status")
                    flushPendingQueue(error = true)
                }
            }
        }
    }

    private fun flushPendingQueue(error: Boolean = false) {
        while (!pendingQueue.isEmpty()) {
            val item = pendingQueue.poll() ?: break
            if (error) {
                item.second()
            } else {
                speak(item.first, item.second)
            }
        }
    }

    fun speak(text: String, onComplete: () -> Unit = {}) {
        if (text.isBlank()) {
            onComplete()
            return
        }
        val ttsEngine = tts
        if (ttsEngine == null) {
            onComplete()
            return
        }

        if (!isInitialized) {
            Log.d("TextToSpeechEngine", "TTS still initializing — queuing utterance: '${text.take(30)}...'")
            pendingQueue.add(Pair(text, onComplete))
            return
        }

        Log.i("TextToSpeechEngine", "Jarvis speaking: '$text'")
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

            override fun onError(uttId: String?, errorCode: Int) {
                Log.w("TextToSpeechEngine", "TTS error ($errorCode) on utterance $uttId")
                if (uttId == utteranceId) onComplete()
            }
        })
        ttsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun setSpeechRate(rate: Float) {
        pendingSpeechRate = rate
        try {
            tts?.setSpeechRate(rate)
        } catch (e: Exception) {
            Log.e("TextToSpeechEngine", "Error setting speech rate", e)
        }
    }

    fun stop() {
        pendingQueue.clear()
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e("TextToSpeechEngine", "Error stopping TTS", e)
        }
    }

    fun shutdown() {
        pendingQueue.clear()
        try {
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (e: Exception) {
            Log.e("TextToSpeechEngine", "Error shutting down TTS", e)
        }
    }
}
