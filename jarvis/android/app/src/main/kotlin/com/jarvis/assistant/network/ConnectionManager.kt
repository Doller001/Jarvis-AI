package com.jarvis.assistant.network

import android.util.Log

class ConnectionManager {
    var state: ConnectionState = ConnectionState.DISCONNECTED
        private set

    fun onConnected() {
        state = ConnectionState.CONNECTED
        Log.i("ConnectionManager", "Jarvis network state: CONNECTED")
    }

    fun onDisconnected() {
        state = ConnectionState.DISCONNECTED
        Log.i("ConnectionManager", "Jarvis network state: DISCONNECTED")
    }
}
