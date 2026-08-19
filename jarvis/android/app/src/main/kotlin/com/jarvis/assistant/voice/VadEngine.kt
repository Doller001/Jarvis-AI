package com.jarvis.assistant.voice

import android.util.Log

class VadEngine {
    var isActive = false
        private set

    fun activate() {
        isActive = true
        Log.d("VadEngine", "Voice Activity Detection activated.")
    }

    fun deactivate() {
        isActive = false
        Log.d("VadEngine", "Voice Activity Detection deactivated.")
    }
}
