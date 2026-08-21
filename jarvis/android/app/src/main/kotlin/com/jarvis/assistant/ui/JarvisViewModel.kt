package com.jarvis.assistant.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.jarvis.assistant.app.RuntimeState
import com.jarvis.assistant.brain.JarvisBrain
import com.jarvis.assistant.brain.JarvisIntent
import com.jarvis.assistant.llm.ProviderManager
import com.jarvis.assistant.memory.MemoryStore
import com.jarvis.assistant.memory.MessageLog
import com.jarvis.assistant.network.ApiClient
import com.jarvis.assistant.network.ConnectionManager
import com.jarvis.assistant.network.ConnectionState
import com.jarvis.assistant.network.WebSocketClient
import com.jarvis.assistant.permissions.PermissionManager
import com.jarvis.assistant.permissions.PermissionState
import com.jarvis.assistant.services.JarvisForegroundService
import com.jarvis.assistant.settings.SettingsManager
import com.jarvis.assistant.voice.VoiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class JarvisUiState(
    val voiceState: VoiceState = VoiceState.STOPPED,
    val wakeListening: Boolean = true,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val runtimeState: RuntimeState = RuntimeState.OFFLINE,
    val permissionState: PermissionState = PermissionState(),
    val activeProvider: String = "Groq",
    val activeModel: String = "llama-3.3-70b-versatile",
    val isAccessibilityEnabled: Boolean = false,
    val lastUtterance: String = "",
    val lastResponse: String = "Ready — listening for 'Jarvis'",
    val messages: List<MessageLog> = emptyList(),
    val backendUrl: String = "https://and9-1.onrender.com",
    val providers: List<String> = listOf("nvidia", "groq", "openrouter", "gemini", "ollama"),
    val providersLoading: Boolean = false,
    val isTtsEnabled: Boolean = true,
    val speechRate: Float = 1.0f,
    val wakeSensitivity: String = "Balanced",
    val pingResult: String? = null,
    val isPinging: Boolean = false
)

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsManager = SettingsManager(application)
    private val permissionManager = PermissionManager(application)
    private val connectionManager = ConnectionManager()
    private val providerManager = ProviderManager()
    private val memoryStore = MemoryStore()
    private val brain = JarvisBrain()
    private val apiClient = ApiClient(baseUrl = settingsManager.backendUrl)
    private val commandExecutor = com.jarvis.assistant.execution.CommandExecutor(application)

    private fun deriveWsUrl(httpUrl: String): String {
        val clean = httpUrl.trim().trimEnd('/')
        return when {
            clean.startsWith("https://") -> clean.replaceFirst("https://", "wss://") + "/ws"
            clean.startsWith("http://") -> clean.replaceFirst("http://", "ws://") + "/ws"
            else -> "wss://$clean/ws"
        }
    }

    private val webSocketClient = WebSocketClient(
        wsUrl = deriveWsUrl(settingsManager.backendUrl),
        connectionManager = connectionManager
    )

    private val _uiState = MutableStateFlow(
        JarvisUiState(
            backendUrl = settingsManager.backendUrl,
            isTtsEnabled = settingsManager.isTtsEnabled,
            speechRate = settingsManager.speechRate,
            wakeSensitivity = settingsManager.wakeSensitivity
        )
    )
    val uiState: StateFlow<JarvisUiState> = _uiState.asStateFlow()

    init {
        refreshPermissions()
        _uiState.update { it.copy(messages = memoryStore.getHistory()) }
        refreshProviders()

        connectionManager.onStateChanged = { state ->
            _uiState.update { it.copy(connectionState = state) }
        }
        webSocketClient.connect()
        pingBackend()

        JarvisForegroundService.onUtterance = { text ->
            val ack = sendUtterance(text)
            if (ack.isNotBlank() && _uiState.value.isTtsEnabled) {
                JarvisForegroundService.speak?.invoke(ack)
            }
        }
        JarvisForegroundService.onResponseDone = {
            _uiState.update { it.copy(voiceState = VoiceState.WAKE_LISTENING) }
        }
        JarvisForegroundService.onStateChanged = { state ->
            _uiState.update { it.copy(voiceState = state) }
        }
        JarvisForegroundService.onWakeToggled = { active ->
            _uiState.update { it.copy(wakeListening = active) }
        }
        ContextCompat.startForegroundService(application, Intent(application, JarvisForegroundService::class.java))
        JarvisForegroundService.setSpeechRate?.invoke(settingsManager.speechRate)
    }

    override fun onCleared() {
        super.onCleared()
        webSocketClient.disconnect()
    }

    fun startListening() {
        JarvisForegroundService.startCommandListening?.invoke()
    }

    fun refreshProviders() {
        _uiState.update { it.copy(providersLoading = true) }
        apiClient.fetchAvailableProviders { providers ->
            _uiState.update { it.copy(providers = providers, providersLoading = false) }
        }
    }

    fun toggleWakeListening() {
        val active = JarvisForegroundService.toggleWakeListening?.invoke() ?: true
        _uiState.update { it.copy(wakeListening = active) }
    }

    fun selectProvider(provider: String, model: String = "") {
        providerManager.selectProviderAndModel(provider, model)
        apiClient.selectProviderOnBackend(provider, model)
        _uiState.update { it.copy(activeProvider = provider, activeModel = model) }
    }

    fun refreshPermissions() {
        val ps = permissionManager.checkPermissionState()
        _uiState.update {
            it.copy(
                permissionState = ps,
                isAccessibilityEnabled = ps.isAccessibilityGranted,
                runtimeState = if (ps.allRequiredGranted) RuntimeState.IDLE else RuntimeState.OFFLINE
            )
        }
    }

    fun updateBackendUrl(newUrl: String) {
        val cleanUrl = newUrl.trim().trimEnd('/')
        if (cleanUrl.isNotBlank()) {
            settingsManager.backendUrl = cleanUrl
            apiClient.baseUrl = cleanUrl
            webSocketClient.updateUrl(deriveWsUrl(cleanUrl))
            _uiState.update { it.copy(backendUrl = cleanUrl) }
            refreshProviders()
            pingBackend(cleanUrl)
        }
    }

    fun pingBackend(urlToTest: String = _uiState.value.backendUrl) {
        _uiState.update { it.copy(isPinging = true, pingResult = null) }
        apiClient.pingBackend(urlToTest) { result ->
            _uiState.update {
                it.copy(
                    isPinging = false,
                    pingResult = result.message
                )
            }
        }
    }

    fun setTtsEnabled(enabled: Boolean) {
        settingsManager.isTtsEnabled = enabled
        _uiState.update { it.copy(isTtsEnabled = enabled) }
    }

    fun setSpeechRate(rate: Float) {
        settingsManager.speechRate = rate
        JarvisForegroundService.setSpeechRate?.invoke(rate)
        _uiState.update { it.copy(speechRate = rate) }
    }

    fun setWakeSensitivity(sensitivity: String) {
        settingsManager.wakeSensitivity = sensitivity
        _uiState.update { it.copy(wakeSensitivity = sensitivity) }
    }

    fun clearHistory() {
        memoryStore.clearHistory()
        _uiState.update { it.copy(messages = emptyList(), lastUtterance = "", lastResponse = "History cleared.") }
    }

    fun deleteMemoryItem(timestamp: Long) {
        memoryStore.deleteMessage(timestamp)
        _uiState.update { it.copy(messages = memoryStore.getHistory()) }
    }

    fun executeQuickAction(commandText: String) {
        sendUtterance(commandText)
    }

    fun sendUtterance(text: String): String {
        if (text.isBlank()) return ""
        memoryStore.recordUserMessage(text)
        val plan = brain.processCommand(text)
        return if (plan.intent is JarvisIntent.Unknown) {
            _uiState.update {
                it.copy(
                    lastUtterance = text,
                    lastResponse = "Thinking…",
                    runtimeState = RuntimeState.ACTING
                )
            }
            routeToCloudBrain(text)
            ""
        } else {
            val ack = commandExecutor.execute(plan.intent)
            memoryStore.recordAssistantMessage(ack)
            _uiState.update {
                it.copy(
                    lastUtterance = text,
                    lastResponse = ack,
                    messages = memoryStore.getHistory(),
                    runtimeState = RuntimeState.ACTING
                )
            }
            ack
        }
    }

    private fun routeToCloudBrain(text: String) {
        apiClient.sendChat(text, "android-device") { answer ->
            val response = if (!answer.isNullOrBlank()) {
                answer
            } else {
                "Connected to Jarvis. Ready for your command."
            }
            memoryStore.recordAssistantMessage(response)
            _uiState.update {
                it.copy(lastResponse = response, messages = memoryStore.getHistory())
            }
            if (_uiState.value.isTtsEnabled) {
                JarvisForegroundService.speak?.invoke(response)
            }
        }
    }
}
