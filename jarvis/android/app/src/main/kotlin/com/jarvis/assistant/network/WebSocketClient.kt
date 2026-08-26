package com.jarvis.assistant.network

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class WebSocketClient(
    var wsUrl: String = "ws://127.0.0.1:8000/ws",
    val connectionManager: ConnectionManager = ConnectionManager(),
    var sessionId: String? = null
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectJob: Job? = null

    private var client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var webSocket: WebSocket? = null
    var onMessageReceived: ((String) -> Unit)? = null
    private var reconnectAttempts = 0
    @Volatile private var disconnectRequested = false

    fun updateUrl(newWsUrl: String) {
        val changed = wsUrl != newWsUrl.trim()
        wsUrl = newWsUrl.trim()
        if (changed && webSocket != null) {
            disconnect()
            connect()
        }
    }

    fun reconnect() {
        disconnect()
        connect()
    }

    private fun scheduleReconnect() {
        if (disconnectRequested) return
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            connectionManager.setConnectionState(ConnectionState.RECONNECTING)
            reconnectAttempts = (reconnectAttempts + 1).coerceAtMost(5)
            val delayMs = (1000L * (1L shl reconnectAttempts)).coerceIn(1500L, 8000L)
            delay(delayMs)
            connect()
        }
    }

    fun connect() {
        disconnectRequested = false
        reconnectJob?.cancel()
        val targetUrl = sessionId?.let { sid ->
            if (wsUrl.contains("session_id=")) wsUrl
            else if (wsUrl.contains("?")) "$wsUrl&session_id=$sid"
            else "$wsUrl?session_id=$sid"
        } ?: wsUrl

        Log.i("WebSocketClient", "Connecting to Jarvis WebSocket at $targetUrl...")
        connectionManager.setConnectionState(ConnectionState.CONNECTING)
        val request = Request.Builder().url(targetUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i("WebSocketClient", "WebSocket Connection Established")
                reconnectAttempts = 0
                connectionManager.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("WebSocketClient", "Received payload: $text")
                onMessageReceived?.invoke(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w("WebSocketClient", "WebSocket failure: ${t.message}. Scheduling auto-reconnect...")
                connectionManager.onDisconnected()
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i("WebSocketClient", "WebSocket closed: $reason ($code)")
                connectionManager.onDisconnected()
            }
        })
    }

    fun sendCommand(text: String, requestId: String) {
        val payload = JSONObject().apply {
            put("type", "command")
            put("request_id", requestId)
            put("text", text)
        }.toString()
        Log.i("WebSocketClient", "Sending command over WebSocket: '$text' (req: $requestId)")
        val sent = webSocket?.send(payload) ?: false
        if (!sent) {
            Log.w("WebSocketClient", "Failed to send command over WebSocket (socket disconnected or closed)")
        }
    }

    fun disconnect() {
        disconnectRequested = true
        reconnectJob?.cancel()
        reconnectJob = null
        webSocket?.close(1000, "Client disconnect requested")
        webSocket = null
        connectionManager.onDisconnected()
    }
}
