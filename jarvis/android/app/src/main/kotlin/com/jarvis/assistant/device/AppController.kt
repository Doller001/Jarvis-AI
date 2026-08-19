package com.jarvis.assistant.device

import android.util.Log

class AppController {
    fun launchApp(appName: String): Boolean {
        Log.i("AppController", "Jarvis launching app: '$appName'")
        return true
    }
}
