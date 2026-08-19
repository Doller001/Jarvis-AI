package com.jarvis.assistant.voice

import android.util.Log

class AudioManager {
    fun routeAudioToSpeaker() {
        Log.d("AudioManager", "Audio routed to speaker.")
    }
    fun routeAudioToBluetooth() {
        Log.d("AudioManager", "Audio routed to Bluetooth SCO.")
    }
}
