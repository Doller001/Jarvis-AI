package com.jarvis.assistant.settings

import android.content.Context
import android.content.SharedPreferences
import com.jarvis.assistant.BuildConfig

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

        // === Manual connectivity keys (jarvis-1.0 offline: user can add API/DB/WS/LLM at runtime) ===
        const val KEY_MANUAL_API_ENABLED = "key_manual_api_enabled"
        const val KEY_MANUAL_BACKEND_URL = "key_manual_backend_url"
        const val KEY_MANUAL_WS_ENABLED = "key_manual_ws_enabled"
        const val KEY_MANUAL_WS_URL = "key_manual_ws_url"
        const val KEY_MANUAL_LLM_ENABLED = "key_manual_llm_enabled"
        const val KEY_MANUAL_LLM_PROVIDER = "key_manual_llm_provider"
        const val KEY_MANUAL_LLM_API_KEY = "key_manual_llm_api_key"
        const val KEY_MANUAL_DB_ENABLED = "key_manual_db_enabled"
        const val KEY_MANUAL_DB_CONNECTION_STRING = "key_manual_db_connection_string"

        const val DEFAULT_BACKEND_URL = "https://jarvis-ai-59qd.onrender.com"
        const val DEFAULT_TTS_ENABLED = true
        const val DEFAULT_SPEECH_RATE = 1.0f
        // Sensitivity: "Low"=0.5f, "Balanced"=0.8f, "High"=1.0f
        const val DEFAULT_WAKE_SENSITIVITY = "Balanced"
        const val DEFAULT_AUTO_START_ON_BOOT = false
        // In the offline flavor the app boots in offline mode by default; online flavor stays online.
        const val DEFAULT_OFFLINE_MODE = BuildConfig.IS_OFFLINE

        // === Defaults for manual connectivity ===
        const val DEFAULT_MANUAL_API_ENABLED = false
        const val DEFAULT_MANUAL_BACKEND_URL = ""
        const val DEFAULT_MANUAL_WS_ENABLED = false
        const val DEFAULT_MANUAL_WS_URL = ""
        const val DEFAULT_MANUAL_LLM_ENABLED = false
        const val DEFAULT_MANUAL_LLM_PROVIDER = ""
        const val DEFAULT_MANUAL_LLM_API_KEY = ""
        const val DEFAULT_MANUAL_DB_ENABLED = false
        const val DEFAULT_MANUAL_DB_CONNECTION_STRING = ""
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

    // === Manual connectivity (jarvis-1.0 offline: add API / WebSocket / LLM / DB at runtime) ===
    var isManualApiEnabled: Boolean
        get() = prefs.getBoolean(KEY_MANUAL_API_ENABLED, DEFAULT_MANUAL_API_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_MANUAL_API_ENABLED, value).apply()

    var manualBackendUrl: String
        get() = prefs.getString(KEY_MANUAL_BACKEND_URL, DEFAULT_MANUAL_BACKEND_URL)?.trim() ?: ""
        set(value) = prefs.edit().putString(KEY_MANUAL_BACKEND_URL, value?.trim() ?: "").apply()

    var isManualWsEnabled: Boolean
        get() = prefs.getBoolean(KEY_MANUAL_WS_ENABLED, DEFAULT_MANUAL_WS_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_MANUAL_WS_ENABLED, value).apply()

    var manualWsUrl: String
        get() = prefs.getString(KEY_MANUAL_WS_URL, DEFAULT_MANUAL_WS_URL)?.trim() ?: ""
        set(value) = prefs.edit().putString(KEY_MANUAL_WS_URL, value?.trim() ?: "").apply()

    var isManualLlmEnabled: Boolean
        get() = prefs.getBoolean(KEY_MANUAL_LLM_ENABLED, DEFAULT_MANUAL_LLM_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_MANUAL_LLM_ENABLED, value).apply()

    var manualLlmProvider: String
        get() = prefs.getString(KEY_MANUAL_LLM_PROVIDER, DEFAULT_MANUAL_LLM_PROVIDER) ?: ""
        set(value) = prefs.edit().putString(KEY_MANUAL_LLM_PROVIDER, value).apply()

    var manualLlmApiKey: String
        get() = prefs.getString(KEY_MANUAL_LLM_API_KEY, DEFAULT_MANUAL_LLM_API_KEY) ?: ""
        set(value) = prefs.edit().putString(KEY_MANUAL_LLM_API_KEY, value).apply()

    var isManualDbEnabled: Boolean
        get() = prefs.getBoolean(KEY_MANUAL_DB_ENABLED, DEFAULT_MANUAL_DB_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_MANUAL_DB_ENABLED, value).apply()

    var manualDbConnectionString: String
        get() = prefs.getString(KEY_MANUAL_DB_CONNECTION_STRING, DEFAULT_MANUAL_DB_CONNECTION_STRING) ?: ""
        set(value) = prefs.edit().putString(KEY_MANUAL_DB_CONNECTION_STRING, value).apply()

    fun hasAnyManualConnectivity(): Boolean =
        isManualApiEnabled || isManualWsEnabled || isManualLlmEnabled || isManualDbEnabled

    fun manualConnectivityCount(): Int {
        var count = 0
        if (isManualApiEnabled && manualBackendUrl.isNotBlank()) count++
        if (isManualWsEnabled && manualWsUrl.isNotBlank()) count++
        if (isManualLlmEnabled && manualLlmProvider.isNotBlank()) count++
        if (isManualDbEnabled && manualDbConnectionString.isNotBlank()) count++
        return count
    }

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
