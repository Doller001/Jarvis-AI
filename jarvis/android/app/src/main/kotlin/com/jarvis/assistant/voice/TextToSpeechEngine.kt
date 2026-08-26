package com.jarvis.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class TextToSpeechEngine(private val context: Context? = null) {
    companion object {
        private const val TAG = "TextToSpeechEngine"
    }

    private var tts: TextToSpeech? = null
    @Volatile
    private var isInitialized = false
    private var pendingSpeechRate: Float = 1.0f
    private val pendingQueue = java.util.concurrent.ConcurrentLinkedQueue<Pair<String, () -> Unit>>()
    private val utteranceCallbacks = java.util.concurrent.ConcurrentHashMap<String, () -> Unit>()

    init {
        context?.let { ctx ->
            tts = TextToSpeech(ctx) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val ttsInst = tts
                    if (ttsInst != null) {
                        configureTts(ttsInst)
                        setupPersistentListener(ttsInst)
                        isInitialized = true
                        Log.i(TAG, "TTS initialized and validated successfully")
                        flushPendingQueue()
                    } else {
                        Log.e(TAG, "TTS instance was null despite SUCCESS status")
                        flushPendingQueue(error = true)
                    }
                } else {
                    Log.e(TAG, "TTS initialization failed with status $status")
                    flushPendingQueue(error = true)
                }
            }
        }
    }

    private fun configureTts(engine: TextToSpeech) {
        // Preferred locale cascade: en-IN -> device default -> en-US -> UK
        val candidates = listOf(
            Locale("en", "IN"),
            Locale.getDefault(),
            Locale.US,
            Locale.UK
        )

        var selectedLocale = Locale.US
        for (loc in candidates) {
            val availability = engine.isLanguageAvailable(loc)
            if (availability >= TextToSpeech.LANG_AVAILABLE) {
                selectedLocale = loc
                engine.language = loc
                break
            }
        }

        try {
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            engine.setAudioAttributes(audioAttributes)
            engine.setSpeechRate(pendingSpeechRate)
        } catch (e: Exception) {
            Log.w(TAG, "Could not set audio attributes on TTS", e)
        }

        Log.i(TAG, "TTS Diagnostic: engine=${engine.defaultEngine}, language=${selectedLocale.toLanguageTag()}, voice=${engine.voice?.name ?: "default"}")
    }

    private fun setupPersistentListener(engine: TextToSpeech) {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(uttId: String?) {
                Log.d(TAG, "TTS playback started: $uttId")
            }

            override fun onDone(uttId: String?) {
                Log.d(TAG, "TTS playback done: $uttId")
                if (uttId != null) {
                    utteranceCallbacks.remove(uttId)?.invoke()
                }
            }

            override fun onStop(uttId: String?, interrupted: Boolean) {
                Log.d(TAG, "TTS playback stopped/interrupted: $uttId (interrupted=$interrupted)")
                if (uttId != null) {
                    utteranceCallbacks.remove(uttId)?.invoke()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(uttId: String?) {
                Log.w(TAG, "TTS playback error: $uttId")
                if (uttId != null) {
                    utteranceCallbacks.remove(uttId)?.invoke()
                }
            }

            override fun onError(uttId: String?, errorCode: Int) {
                Log.w(TAG, "TTS playback error ($errorCode) on $uttId")
                if (uttId != null) {
                    utteranceCallbacks.remove(uttId)?.invoke()
                }
            }
        })
    }

    private fun flushPendingQueue(error: Boolean = false) {
        val items = mutableListOf<Pair<String, () -> Unit>>()
        while (!pendingQueue.isEmpty()) {
            val item = pendingQueue.poll() ?: break
            items.add(item)
        }
        if (items.isEmpty()) return

        if (error) {
            items.forEach { it.second() }
        } else {
            // Keep the last utterance to speak and invoke previous callbacks so state does not hang
            for (i in 0 until items.size - 1) {
                items[i].second()
            }
            val last = items.last()
            speak(last.first, last.second)
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
            Log.d(TAG, "TTS still initializing — queuing utterance: '${text.take(30)}...'")
            pendingQueue.add(Pair(text, onComplete))
            return
        }

        val utteranceId = "JARVIS_TTS_${System.currentTimeMillis()}_${(100..999).random()}"
        utteranceCallbacks[utteranceId] = onComplete

        Log.i(TAG, "Jarvis speaking (id=$utteranceId): '$text'")
        val result = ttsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            Log.e(TAG, "tts.speak failed with error code $result for utterance $utteranceId")
            utteranceCallbacks.remove(utteranceId)?.invoke()
        }
    }

    fun setSpeechRate(rate: Float) {
        pendingSpeechRate = rate
        try {
            tts?.setSpeechRate(rate)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting speech rate", e)
        }
    }

    fun stop() {
        pendingQueue.clear()
        utteranceCallbacks.values.forEach { it.invoke() }
        utteranceCallbacks.clear()
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS", e)
        }
    }

    fun shutdown() {
        pendingQueue.clear()
        utteranceCallbacks.values.forEach { it.invoke() }
        utteranceCallbacks.clear()
        try {
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        }
    }
}
