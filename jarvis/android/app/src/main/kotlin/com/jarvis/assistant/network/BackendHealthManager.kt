package com.jarvis.assistant.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.jarvis.assistant.telemetry.DiagnosticEventBus
import com.jarvis.assistant.telemetry.TelemetryEventType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

enum class HealthStatus {
    CONNECTED,
    CONNECTING,
    DEGRADED,
    OFFLINE
}

data class BackendHealth(
    val status: HealthStatus = HealthStatus.CONNECTED,
    val httpHealthy: Boolean = true,
    val wsConnected: Boolean = false,
    val isNetworkAvailable: Boolean = true,
    val lastSuccessAt: Long? = null,
    val retryAttempt: Int = 0,
    val reason: String? = null,
    val endpoint: String = "https://jarvis-ai-59qd.onrender.com",
    val isOfflineMode: Boolean = false
)

/**
 * Single source of truth for Backend & Network connectivity.
 * Defaults to Online mode. Offline mode only active when explicitly enabled.
 *
 * Coordinates:
 *  1. Android ConnectivityManager NetworkCallback (triggers instant reconnect on network restoration).
 *  2. HTTP health probes with latency measurement.
 *  3. WebSocket connection management with exponential backoff & jitter.
 *  4. Clean separation: local assistant remains 100% operational even when backend is offline.
 */
class BackendHealthManager(
    private val context: Context?,
    private val apiClient: ApiClient = ApiClient(),
    private val webSocketClient: WebSocketClient = WebSocketClient(),
    private val authTokenManager: AuthTokenManager? = null
) {
    companion object {
        private const val TAG = "BackendHealthManager"
        private const val DEFAULT_ENDPOINT = "https://jarvis-ai-59qd.onrender.com"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _health = MutableStateFlow(BackendHealth(endpoint = DEFAULT_ENDPOINT))
    val health: StateFlow<BackendHealth> = _health.asStateFlow()

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var healthCheckJob: Job? = null
    private var retryAttempt = 0

    fun start(isOfflineMode: Boolean = false) {
        val deviceId = context?.let { com.jarvis.assistant.settings.SettingsManager(it).deviceId }
        if (deviceId != null) {
            webSocketClient.sessionId = deviceId
        }
        if (isOfflineMode) {
            setOfflineMode(true)
            return
        }
        registerNetworkCallback()
        setupWebSocketListeners()
        startPeriodicHealthCheck()
        checkHealthAndReconnect()
    }

    fun setOfflineMode(enabled: Boolean) {
        if (enabled) {
            healthCheckJob?.cancel()
            webSocketClient.disconnect()
            _health.value = _health.value.copy(
                isOfflineMode = true,
                status = HealthStatus.OFFLINE,
                httpHealthy = false,
                wsConnected = false,
                reason = "Offline mode activated by user"
            )
        } else {
            _health.value = _health.value.copy(
                isOfflineMode = false,
                isNetworkAvailable = true,
                status = HealthStatus.CONNECTED,
                reason = null
            )
            registerNetworkCallback()
            setupWebSocketListeners()
            startPeriodicHealthCheck()
            checkHealthAndReconnect()
        }
    }

    private fun registerNetworkCallback() {
        val ctx = context ?: return
        if (networkCallback != null) return
        try {
            connectivityManager = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (_health.value.isOfflineMode) return
                    Log.i(TAG, "Internet network available — triggering immediate backend health check & reconnect")
                    _health.value = _health.value.copy(isNetworkAvailable = true)
                    DiagnosticEventBus.emit(
                        type = TelemetryEventType.NETWORK_AVAILABLE,
                        component = TAG,
                        details = mapOf("available" to true)
                    )
                    retryAttempt = 0
                    checkHealthAndReconnect()
                }

                override fun onLost(network: Network) {
                    if (_health.value.isOfflineMode) return
                    Log.w(TAG, "Internet network lost")
                    _health.value = _health.value.copy(
                        isNetworkAvailable = false,
                        status = HealthStatus.CONNECTING,
                        reason = "Network connection lost, waiting to reconnect"
                    )
                    DiagnosticEventBus.emit(
                        type = TelemetryEventType.NETWORK_LOST,
                        component = TAG,
                        errorMessage = "Network lost"
                    )
                }
            }

            networkCallback?.let {
                connectivityManager?.registerNetworkCallback(request, it)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register NetworkCallback: ${e.message}")
        }
    }

    private fun setupWebSocketListeners() {
        webSocketClient.connectionManager.onStateChanged = { connState ->
            if (!_health.value.isOfflineMode) {
                val isWsConnected = connState == ConnectionState.CONNECTED
                updateHealthState(wsConnected = isWsConnected)
            }
        }
    }

    fun updateEndpoint(newUrl: String): Boolean {
        val clean = normalizeUrl(newUrl) ?: return false
        _health.value = _health.value.copy(endpoint = clean)
        apiClient.baseUrl = clean
        val wsUrl = clean.replace("http://", "ws://").replace("https://", "wss://") + "/ws"
        webSocketClient.updateUrl(wsUrl)
        if (!_health.value.isOfflineMode) {
            checkHealthAndReconnect()
        }
        return true
    }

    fun checkHealthAndReconnect() {
        if (_health.value.isOfflineMode) return
        scope.launch {
            val endpoint = _health.value.endpoint
            apiClient.pingBackend(endpoint) { pingResult ->
                if (_health.value.isOfflineMode) return@pingBackend
                val httpOk = pingResult.isSuccess
                val lastSuccess = if (httpOk) System.currentTimeMillis() else _health.value.lastSuccessAt
                updateHealthState(httpHealthy = httpOk, lastSuccess = lastSuccess, reason = if (!httpOk) pingResult.message else null)

                if (httpOk && !webSocketClient.connectionManager.isConnected) {
                    webSocketClient.connect()
                }
            }
        }
    }

    private fun updateHealthState(
        httpHealthy: Boolean = _health.value.httpHealthy,
        wsConnected: Boolean = _health.value.wsConnected,
        lastSuccess: Long? = _health.value.lastSuccessAt,
        reason: String? = _health.value.reason
    ) {
        if (_health.value.isOfflineMode) {
            _health.value = _health.value.copy(
                status = HealthStatus.OFFLINE,
                httpHealthy = false,
                wsConnected = false,
                reason = "Offline mode activated by user"
            )
            return
        }

        val status = when {
            httpHealthy && wsConnected -> HealthStatus.CONNECTED
            httpHealthy || wsConnected -> HealthStatus.CONNECTED
            _health.value.isNetworkAvailable -> HealthStatus.CONNECTING
            else -> HealthStatus.CONNECTING
        }

        _health.value = _health.value.copy(
            status = status,
            httpHealthy = httpHealthy,
            wsConnected = wsConnected,
            lastSuccessAt = lastSuccess,
            reason = reason
        )

        DiagnosticEventBus.emit(
            type = TelemetryEventType.BACKEND_STATUS_CHANGED,
            component = TAG,
            details = mapOf(
                "status" to status.name,
                "http" to httpHealthy,
                "ws" to wsConnected
            )
        )
    }

    private fun startPeriodicHealthCheck() {
        healthCheckJob?.cancel()
        healthCheckJob = scope.launch {
            while (isActive) {
                if (!_health.value.isOfflineMode && _health.value.isNetworkAvailable) {
                    checkHealthAndReconnect()
                }
                // Periodic probe every 30 seconds
                delay(30_000L)
            }
        }
    }

    private fun normalizeUrl(url: String): String? {
        val trimmed = url.trim().trimEnd('/')
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.isNotBlank() -> "https://$trimmed"
            else -> null
        }
    }

    fun release() {
        healthCheckJob?.cancel()
        networkCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } catch (_: Exception) {}
            networkCallback = null
        }
        webSocketClient.disconnect()
        scope.cancel()
    }
}
