package com.jarvis.assistant.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class JarvisUiState(
    val voiceState: VoiceState = VoiceState.IDLE,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val runtimeState: RuntimeState = RuntimeState.OFFLINE,
    val permissionState: PermissionState = PermissionState(),
    val activeProvider: String = "Groq",
    val activeModel: String = "llama-3.3-70b-versatile",
    val isAccessibilityEnabled: Boolean = false,
    val lastUtterance: String = "",
    val lastResponse: String = "JARVIS online. Good to see you, Minaty. What shall we build today?",
    val messages: List<MessageLog> = emptyList(),
    val backendUrl: String = "https://and9-1.onrender.com",
    val providers: List<String> = listOf("nvidia", "groq", "openrouter", "gemini", "ollama"),
    val providersLoading: Boolean = false,
    val isTtsEnabled: Boolean = true,
    val speechRate: Float = 1.0f,
    val wakeListening: Boolean = false,
    val wakeSensitivity: String = "Balanced",
    val pingResult: String? = null,
    val isPinging: Boolean = false,
    val environmentProfile: String = "Indoor (Quiet)",
    val noiseFloorDb: Float = -58f,
    val audioSnrDb: Float = 0f,
    val isOverlayActive: Boolean = false
)

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsManager = SettingsManager(application)
    private val permissionManager = PermissionManager(application)
    private val connectionManager = ConnectionManager()
    private val providerManager = ProviderManager()
    // Opening and warming the SQLite memory store can take noticeable time on a
    // cold start. Keep it lazy and only create it from the IO dispatcher.
    private val memoryRouter by lazy { com.jarvis.assistant.memory.MemoryDecisionRouter(application) }
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
            speechRate = settingsManager.speechRate
        )
    )
    val uiState: StateFlow<JarvisUiState> = _uiState.asStateFlow()
    private var commandJob: Job? = null

    init {
        refreshPermissions()
        viewModelScope.launch(Dispatchers.IO) {
            val messages = memoryRouter.getEngine().getRecentEpisodes()
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(messages = messages) }
            }
        }

        connectionManager.onStateChanged = { state ->
            _uiState.update { it.copy(connectionState = state) }
        }
        webSocketClient.onMessageReceived = { payload ->
            handleWebSocketMessage(payload)
        }
        JarvisForegroundService.onUtterance = { text ->
            sendUtterance(text)
        }
        JarvisForegroundService.onResponseDone = {
            _uiState.update { it.copy(voiceState = VoiceState.IDLE) }
        }
        JarvisForegroundService.onStateChanged = { state ->
            _uiState.update { it.copy(voiceState = state) }
        }
        JarvisForegroundService.onWakeToggled = { active ->
            _uiState.update { it.copy(wakeListening = active) }
        }
        JarvisForegroundService.onEnvironmentChanged = { env ->
            _uiState.update { it.copy(environmentProfile = env.displayName) }
        }
        JarvisForegroundService.onAudioMetrics = { metrics ->
            _uiState.update { it.copy(noiseFloorDb = metrics.noiseFloorDb, audioSnrDb = metrics.snrDb) }
        }
        // Connect independently of the Providers screen so Home and voice
        // flows do not incorrectly show the backend as offline.
        connectBackend()
    }

    private fun handleWebSocketMessage(payload: String) {
        try {
            val json = org.json.JSONObject(payload)
            val responseText = json.optString("response_text")
                .ifBlank { json.optString("result") }
                .ifBlank { json.optString("message") }
                .ifBlank { json.optString("prompt") }
            if (responseText.isNotBlank()) {
                viewModelScope.launch(Dispatchers.IO) {
                    val engine = memoryRouter.getEngine()
                    engine.recordEpisode("assistant", responseText)
                    updateCompletedResponse(_uiState.value.lastUtterance, responseText, RuntimeState.IDLE, engine)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("JarvisViewModel", "Error parsing WebSocket message: ${e.message}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        commandJob?.cancel()
        webSocketClient.disconnect()
    }

    fun startListening() {
        if (JarvisForegroundService.isRunning && JarvisForegroundService.startCommandListening != null) {
            JarvisForegroundService.startCommandListening?.invoke()
        } else {
            try {
                ContextCompat.startForegroundService(
                    getApplication(), Intent(getApplication(), JarvisForegroundService::class.java).apply {
                        action = JarvisForegroundService.ACTION_LISTEN_FOR_COMMAND
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("JarvisViewModel", "Failed to start JarvisForegroundService", e)
            }
        }
    }

    fun refreshProviders() {
        _uiState.update { it.copy(providersLoading = true) }
        apiClient.fetchAvailableProviders { providers ->
            _uiState.update { it.copy(providers = providers, providersLoading = false) }
        }
    }

    fun connectBackend() {
        webSocketClient.connect()
    }

    fun selectProvider(provider: String, model: String = "") {
        val resolvedModel = if (model.isNotBlank()) model else providerManager.getModelsForProvider(provider).firstOrNull().orEmpty()
        providerManager.selectProviderAndModel(provider, resolvedModel)
        apiClient.selectProviderOnBackend(provider, resolvedModel)
        _uiState.update { it.copy(activeProvider = provider, activeModel = resolvedModel) }
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

    fun toggleWakeListening() {
        if (!JarvisForegroundService.isRunning) {
            try {
                ContextCompat.startForegroundService(
                    getApplication(), Intent(getApplication(), JarvisForegroundService::class.java).apply {
                        action = JarvisForegroundService.ACTION_START
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("JarvisViewModel", "Failed to start JarvisForegroundService for wake toggle", e)
            }
        }
        val active = JarvisForegroundService.toggleWakeListening?.invoke() ?: (!settingsManager.wakeWordEnabled)
        settingsManager.wakeWordEnabled = active
        _uiState.update { it.copy(wakeListening = active) }
    }

    fun setWakeSensitivity(sensitivity: String) {
        settingsManager.wakeSensitivity = sensitivity
        JarvisForegroundService.setWakeSensitivity?.invoke(sensitivity)
        _uiState.update { it.copy(wakeSensitivity = sensitivity) }
    }

    fun toggleOverlay() {
        val context = getApplication<Application>()
        if (com.jarvis.assistant.overlay.OverlayController.hasOverlayPermission(context)) {
            if (com.jarvis.assistant.services.JarvisOverlayService.isRunning) {
                com.jarvis.assistant.overlay.OverlayController.hide(context)
                _uiState.update { it.copy(isOverlayActive = false) }
            } else {
                com.jarvis.assistant.overlay.OverlayController.show(context)
                _uiState.update { it.copy(isOverlayActive = true) }
            }
        } else {
            com.jarvis.assistant.overlay.OverlayController.openPermissionSettings(context)
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            memoryRouter.getEngine().clearAllHistory()
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(messages = emptyList(), lastUtterance = "", lastResponse = "History cleared.") }
            }
        }
    }

    fun deleteMemoryItem(timestamp: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val engine = memoryRouter.getEngine()
            engine.deleteEpisode(timestamp)
            val messages = engine.getRecentEpisodes()
            withContext(Dispatchers.Main) { _uiState.update { it.copy(messages = messages) } }
        }
    }

    fun executeQuickAction(commandText: String) {
        sendUtterance(commandText)
    }

    fun sendUtterance(text: String): String {
        if (text.isBlank()) return ""
        commandJob?.cancel()
        _uiState.update {
            it.copy(lastUtterance = text, lastResponse = "Thinking…", runtimeState = RuntimeState.THINKING)
        }
        commandJob = viewModelScope.launch(Dispatchers.IO) { processUtterance(text) }
        // Voice responses are delivered when the background command finishes;
        // never make the audio callback wait for SQLite, package-manager or I/O.
        return ""
    }

    private suspend fun processUtterance(text: String) {
        val engine = memoryRouter.getEngine()
        engine.recordEpisode("user", text)

        // 1. Check Router for FAST CAG path (< 1ms)
        val routed = memoryRouter.route(text)
        if (routed.source == com.jarvis.assistant.memory.RouteSource.FAST_CAG_EXACT ||
            routed.source == com.jarvis.assistant.memory.RouteSource.FAST_CAG_NEAR) {
            val cachedAnswer = routed.text
            engine.recordEpisode("assistant", cachedAnswer)
            updateCompletedResponse(text, cachedAnswer, RuntimeState.ACTING, engine)
            return
        }

        // 2. Check local command executor
        val plan = brain.processCommand(text)
        if (plan.intent is JarvisIntent.Unknown) {
            routeToCloudBrain(text, routed, engine)
        } else if (plan.requiresConfirmation) {
            val response = "Confirmation required: ${plan.confirmationPrompt}"
            engine.recordEpisode("assistant", response)
            updateCompletedResponse(text, response, RuntimeState.IDLE, engine)
        } else {
            val ack = commandExecutor.execute(plan.intent)
            engine.recordEpisode("assistant", ack)
            memoryRouter.learn(text, ack)
            updateCompletedResponse(text, ack, RuntimeState.ACTING, engine)
        }
    }

    private suspend fun updateCompletedResponse(
        text: String,
        response: String,
        state: RuntimeState,
        engine: com.jarvis.assistant.memory.MemoryEngine
    ) {
        val messages = engine.getRecentEpisodes()
        withContext(Dispatchers.Main) {
            val shouldSpeak = _uiState.value.isTtsEnabled
            _uiState.update {
                it.copy(
                    lastUtterance = text,
                    lastResponse = response,
                    messages = messages,
                    runtimeState = state
                )
            }
            if (shouldSpeak) JarvisForegroundService.speak?.invoke(response)
        }
    }

    private fun routeToCloudBrain(
        text: String,
        routed: com.jarvis.assistant.memory.RoutedAnswer,
        engine: com.jarvis.assistant.memory.MemoryEngine
    ) {
        var promptText = text
        if (routed.ragContext.isNotEmpty() || routed.userFacts.isNotEmpty()) {
            val ragInfo = routed.ragContext.joinToString("; ") { it.text }
            val factInfo = routed.userFacts.joinToString("; ") { it.text }
            val contextParts = mutableListOf<String>()
            if (factInfo.isNotBlank()) contextParts.add("Known user facts: $factInfo")
            if (ragInfo.isNotBlank()) contextParts.add("Knowledge context: $ragInfo")
            if (contextParts.isNotEmpty()) {
                promptText = "${contextParts.joinToString(" | ")}\nUser Question: $text"
            }
        }

        apiClient.sendChat(promptText, "android-device") { answer ->
            viewModelScope.launch(Dispatchers.IO) {
                val response = answer ?: "JARVIS is operating in local mode. All on-device systems, hardware controls, and local memories are active."
                engine.recordEpisode("assistant", response)
                memoryRouter.learn(text, response)
                updateCompletedResponse(text, response, RuntimeState.IDLE, engine)
            }
        }
    }
}
