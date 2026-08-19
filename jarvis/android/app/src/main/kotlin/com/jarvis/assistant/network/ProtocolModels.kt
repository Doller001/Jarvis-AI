package com.jarvis.assistant.network

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING }

data class JarvisCommandMessage(
    val type: String = "command",
    val requestId: String,
    val sessionId: String = "android-client-1",
    val text: String
)

data class JarvisConfirmationMessage(
    val type: String = "confirmation",
    val requestId: String,
    val sessionId: String = "android-client-1",
    val confirmationToken: String,
    val confirmed: Boolean
)
