package com.jarvis.assistant.device

import android.util.Log

class SystemController {
    fun toggleWifi(enable: Boolean) {
        Log.i("SystemController", "Toggling Wi-Fi -> $enable")
    }

    fun toggleBluetooth(enable: Boolean) {
        Log.i("SystemController", "Toggling Bluetooth -> $enable")
    }

    fun toggleTorch(enable: Boolean) {
        Log.i("SystemController", "Toggling Torch -> $enable")
    }

    fun setVolume(levelPercentage: Int) {
        Log.i("SystemController", "Setting volume to $levelPercentage%")
    }
}
