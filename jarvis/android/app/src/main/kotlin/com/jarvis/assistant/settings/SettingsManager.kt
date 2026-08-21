package com.jarvis.assistant.settings

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("jarvis_settings_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_BACKEND_URL = "key_backend_url"
        const val KEY_TTS_ENABLED = "key_tts_enabled"
        const val KEY_SPEECH_RATE = "key_speech_rate"
        const val KEY_WAKE_SENSITIVITY = "key_wake_sensitivity"

        const val DEFAULT_BACKEND_URL = "https://and9-1.onrender.com"
        const val DEFAULT_TTS_ENABLED = true
        const val DEFAULT_SPEECH_RATE = 1.0f
        const val DEFAULT_WAKE_SENSITIVITY = "Balanced"
    }

    var backendUrl: String
        get() = prefs.getString(KEY_BACKEND_URL, DEFAULT_BACKEND_URL) ?: DEFAULT_BACKEND_URL
        set(value) = prefs.edit().putString(KEY_BACKEND_URL, value.trim()).apply()

    var isTtsEnabled: Boolean
        get() = prefs.getBoolean(KEY_TTS_ENABLED, DEFAULT_TTS_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_TTS_ENABLED, value).apply()

    var speechRate: Float
        get() = prefs.getFloat(KEY_SPEECH_RATE, DEFAULT_SPEECH_RATE)
        set(value) = prefs.edit().putFloat(KEY_SPEECH_RATE, value).apply()

    var wakeSensitivity: String
        get() = prefs.getString(KEY_WAKE_SENSITIVITY, DEFAULT_WAKE_SENSITIVITY) ?: DEFAULT_WAKE_SENSITIVITY
        set(value) = prefs.edit().putString(KEY_WAKE_SENSITIVITY, value).apply()
}
