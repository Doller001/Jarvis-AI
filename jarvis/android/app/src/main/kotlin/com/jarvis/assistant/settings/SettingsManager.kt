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
        const val KEY_WAKE_ENABLED = "key_wake_enabled"
        const val KEY_WAKE_SENSITIVITY = "key_wake_sensitivity"
        const val KEY_AUTO_START_ON_BOOT = "key_auto_start_on_boot"
        const val KEY_OFFLINE_MODE = "key_offline_mode"

        const val DEFAULT_BACKEND_URL = "https://jarvis-ai-59qd.onrender.com"
        const val DEFAULT_TTS_ENABLED = true
        const val DEFAULT_SPEECH_RATE = 1.0f
        // Sensitivity: "Low"=0.5f, "Balanced"=0.8f, "High"=1.0f
        const val DEFAULT_WAKE_SENSITIVITY = "Balanced"
        const val DEFAULT_AUTO_START_ON_BOOT = false
        const val DEFAULT_OFFLINE_MODE = false
    }

    var isOfflineMode: Boolean
        get() = prefs.getBoolean(KEY_OFFLINE_MODE, DEFAULT_OFFLINE_MODE)
        set(value) = prefs.edit().putBoolean(KEY_OFFLINE_MODE, value).apply()

    var backendUrl: String
        get() {
            val stored = prefs.getString(KEY_BACKEND_URL, null)?.trim()
            // Migrate endpoints shipped by older APKs, but retain any endpoint
            // the user explicitly configured.
            return when (stored) {
                null, "", "http://127.0.0.1:8000", "https://and9-1.onrender.com" -> DEFAULT_BACKEND_URL
                else -> stored
            }
        }
        set(value) = prefs.edit().putString(KEY_BACKEND_URL, value.trim()).apply()

    var isTtsEnabled: Boolean
        get() = prefs.getBoolean(KEY_TTS_ENABLED, DEFAULT_TTS_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_TTS_ENABLED, value).apply()

    var speechRate: Float
        get() = prefs.getFloat(KEY_SPEECH_RATE, DEFAULT_SPEECH_RATE)
        set(value) = prefs.edit().putFloat(KEY_SPEECH_RATE, value).apply()

    var wakeWordEnabled: Boolean
        get() = prefs.getBoolean(KEY_WAKE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_WAKE_ENABLED, value).apply()

    var wakeSensitivity: String
        get() = prefs.getString(KEY_WAKE_SENSITIVITY, DEFAULT_WAKE_SENSITIVITY) ?: DEFAULT_WAKE_SENSITIVITY
        set(value) = prefs.edit().putString(KEY_WAKE_SENSITIVITY, value).apply()

    var autoStartOnBoot: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START_ON_BOOT, DEFAULT_AUTO_START_ON_BOOT)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START_ON_BOOT, value).apply()

    val deviceId: String
        get() {
            var id = prefs.getString("key_device_id", null)
            if (id.isNullOrBlank()) {
                id = "device_${java.util.UUID.randomUUID().toString().take(12)}"
                prefs.edit().putString("key_device_id", id).apply()
            }
            return id
        }
}
