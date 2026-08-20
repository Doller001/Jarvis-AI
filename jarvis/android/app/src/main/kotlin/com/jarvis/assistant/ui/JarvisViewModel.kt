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
import com.jarvis.assistant.permissions.PermissionManager
import com.jarvis.assistant.permissions.PermissionState
import com.jarvis.assistant.services.JarvisForegroundService
import com.jarvis.assistant.voice.VoiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class JarvisUiState(
    val voiceState: VoiceState = VoiceState.STOPPED,
    val wakeListening: Boolean = true,
    val connectionState: com.jarvis.assistant.network.ConnectionState =
        com.jarvis.assistant.network.ConnectionState.DISCONNECTED,
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
    val providersLoading: Boolean = false
)

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val permissionManager = PermissionManager(application)
    private val connectionManager = ConnectionManager()
    private val providerManager = ProviderManager()
    private val memoryStore = MemoryStore()
    private val brain = JarvisBrain()
    private val apiClient = ApiClient()

    private val _uiState = MutableStateFlow(JarvisUiState())
    val uiState: StateFlow<JarvisUiState> = _uiState.asStateFlow()

    init {
        refreshPermissions()
        _uiState.update { it.copy(messages = memoryStore.getHistory()) }
        refreshProviders()
        JarvisForegroundService.onUtterance = { text ->
            val ack = sendUtterance(text)
            if (ack.isNotBlank()) JarvisForegroundService.speak?.invoke(ack)
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

    fun sendUtterance(text: String): String {
        if (text.isBlank()) return ""
        memoryStore.recordUserMessage(text)
        val plan = brain.processCommand(text)
        val ack: String = when (val intent = plan.intent) {
            is JarvisIntent.CallContact -> "Calling ${intent.contactName}…"
            is JarvisIntent.SendSms -> "Sending SMS to ${intent.recipient}…"
            is JarvisIntent.SendWhatsApp -> "Sending WhatsApp message to ${intent.contactName}…"
            is JarvisIntent.OpenApp -> "Opening ${intent.appName}…"
            is JarvisIntent.ToggleTorch -> "Torch turned ${intent.state}."
            is JarvisIntent.ToggleWifi -> "Wi-Fi turned ${intent.state}."
            is JarvisIntent.ToggleBluetooth -> "Bluetooth turned ${intent.state}."
            is JarvisIntent.SetVolume -> "Volume set to ${intent.level}."
            is JarvisIntent.GetTime -> "Checking the current time…"
            is JarvisIntent.GetBattery -> "Reading battery level…"
            is JarvisIntent.ReadScreen -> "Reading the screen…"
            is JarvisIntent.Unknown -> "I heard: \"$text\". I'll route that to the cloud brain."
        }
        memoryStore.recordAssistantMessage(ack)
        _uiState.update {
            it.copy(
                lastUtterance = text,
                lastResponse = ack,
                messages = memoryStore.getHistory(),
                runtimeState = RuntimeState.ACTING
            )
        }
        return ack
    }
}
