package com.jarvis.assistant.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.app.RuntimeState
import com.jarvis.assistant.execution.ExecutionOutcome
import com.jarvis.assistant.execution.TaskExecutionCoordinator
import com.jarvis.assistant.llm.ProviderManager
import com.jarvis.assistant.memory.MessageLog
import com.jarvis.assistant.network.*
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
    private val providerManager = ProviderManager()
    private val memoryRouter by lazy { com.jarvis.assistant.memory.MemoryDecisionRouter(application) }
    private val coordinator by lazy { TaskExecutionCoordinator(application) }
    private val apiClient = ApiClient(baseUrl = settingsManager.backendUrl)
    private val backendHealthManager = BackendHealthManager(application, apiClient)

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

        // Single Source of Truth for Backend Health & Connectivity
        backendHealthManager.start()
        viewModelScope.launch {
            backendHealthManager.health.collect { health ->
                val connState = when (health.status) {
                    HealthStatus.CONNECTED -> ConnectionState.CONNECTED
                    HealthStatus.CONNECTING -> ConnectionState.CONNECTING
                    HealthStatus.DEGRADED -> ConnectionState.RECONNECTING
                    HealthStatus.OFFLINE -> ConnectionState.DISCONNECTED
                }
                _uiState.update { it.copy(connectionState = connState) }
            }
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
    }

    override fun onCleared() {
        super.onCleared()
        commandJob?.cancel()
        backendHealthManager.release()
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
        backendHealthManager.checkHealthAndReconnect()
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
        val ok = backendHealthManager.updateEndpoint(newUrl)
        if (ok) {
            val clean = newUrl.trim().trimEnd('/')
            settingsManager.backendUrl = clean
            _uiState.update { it.copy(backendUrl = clean) }
            refreshProviders()
            pingBackend(clean)
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
        return ""
    }

    private suspend fun processUtterance(text: String) {
        val engine = memoryRouter.getEngine()
        engine.recordEpisode("user", text)

        val outcome = coordinator.coordinate(text, memoryRouter)

        when (outcome) {
            is ExecutionOutcome.Success -> {
                engine.recordEpisode("assistant", outcome.spokenResponse)
                if (outcome.isLocalAction) memoryRouter.learn(text, outcome.spokenResponse)
                updateCompletedResponse(text, outcome.spokenResponse, RuntimeState.ACTING, engine)
            }
            is ExecutionOutcome.ConfirmationRequired -> {
                engine.recordEpisode("assistant", outcome.prompt)
                updateCompletedResponse(text, outcome.prompt, RuntimeState.IDLE, engine)
            }
            is ExecutionOutcome.Failure -> {
                engine.recordEpisode("assistant", outcome.spokenResponse)
                updateCompletedResponse(text, outcome.spokenResponse, RuntimeState.IDLE, engine)
            }
            is ExecutionOutcome.RouteToCloud -> {
                val routed = memoryRouter.route(text)
                routeToCloudBrain(text, routed, engine)
            }
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
