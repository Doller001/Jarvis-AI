package com.jarvis.assistant.app

import android.app.Application
import android.content.ComponentCallbacks2
import android.util.Log

enum class RuntimeState { IDLE, LISTENING, THINKING, ACTING, SPEAKING, ERROR, OFFLINE }

class JarvisApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i("JarvisApplication", "Initializing Jarvis AI Application Runtime...")
    }

    /**
     * Cooperate with Android OS and OEM Low-RAM Killers (HyperOS, OneUI, ColorOS)
     * by trimming memory when system memory pressure increases.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                Log.w("JarvisApplication", "High memory pressure ($level) — trimming memory and caches")
                System.gc()
            }
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                Log.d("JarvisApplication", "UI hidden — releasing volatile UI resources")
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.w("JarvisApplication", "Low memory warning — triggering GC and cache clean")
        System.gc()
    }
}
