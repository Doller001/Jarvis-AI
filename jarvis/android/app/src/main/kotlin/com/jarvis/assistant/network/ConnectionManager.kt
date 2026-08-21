package com.jarvis.assistant.network

import android.util.Log

class ConnectionManager {
    var onStateChanged: ((ConnectionState) -> Unit)? = null

    var state: ConnectionState = ConnectionState.DISCONNECTED
        private set

    fun setConnectionState(newState: ConnectionState) {
        state = newState
        Log.i("ConnectionManager", "Jarvis network state: $newState")
        onStateChanged?.invoke(newState)
    }

    fun onConnected() {
        state = ConnectionState.CONNECTED
        Log.i("ConnectionManager", "Jarvis network state: CONNECTED")
        onStateChanged?.invoke(ConnectionState.CONNECTED)
    }

    fun onDisconnected() {
        state = ConnectionState.DISCONNECTED
        Log.i("ConnectionManager", "Jarvis network state: DISCONNECTED")
        onStateChanged?.invoke(ConnectionState.DISCONNECTED)
    }
}
