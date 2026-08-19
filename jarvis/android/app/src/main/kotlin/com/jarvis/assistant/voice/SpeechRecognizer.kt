package com.jarvis.assistant.voice

import android.util.Log

class SpeechRecognizer {
    private var isListening = false

    fun startListening(onResult: (String) -> Unit) {
        isListening = true
        Log.d("SpeechRecognizer", "STT engine listening...")
    }

    fun stopListening() {
        isListening = false
    }
}
