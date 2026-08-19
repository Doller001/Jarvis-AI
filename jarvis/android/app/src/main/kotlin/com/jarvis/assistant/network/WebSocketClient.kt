package com.jarvis.assistant.network

import android.util.Log

class WebSocketClient(
    val wsUrl: String = "ws://10.0.2.2:8000/ws",
    private val connectionManager: ConnectionManager = ConnectionManager()
) {
    fun connect() {
        Log.i("WebSocketClient", "Connecting to Jarvis WebSocket at $wsUrl...")
        connectionManager.onConnected()
    }

    fun sendCommand(text: String, requestId: String) {
        Log.i("WebSocketClient", "Sending command over WebSocket: '$text' (req: $requestId)")
    }

    fun disconnect() {
        connectionManager.onDisconnected()
    }
}
