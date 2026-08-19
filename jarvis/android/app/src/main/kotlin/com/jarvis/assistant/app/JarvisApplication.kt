package com.jarvis.assistant.app

import android.app.Application
import android.util.Log

enum class RuntimeState { IDLE, LISTENING, THINKING, ACTING, SPEAKING, ERROR, OFFLINE }

class JarvisApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i("JarvisApplication", "Initializing Jarvis AI Application Runtime...")
    }
}
