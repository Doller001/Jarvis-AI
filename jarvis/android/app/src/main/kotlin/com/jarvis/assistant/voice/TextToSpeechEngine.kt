package com.jarvis.assistant.voice

import android.util.Log

class TextToSpeechEngine {
    fun speak(text: String, onComplete: () -> Unit = {}) {
        Log.i("TextToSpeechEngine", "Jarvis speaking: '$text'")
        onComplete()
    }
}
