package com.jarvis.assistant.app

import com.jarvis.assistant.voice.VoiceState

data class AppState(
    val voiceState: VoiceState = VoiceState.DISABLED,
    val activeProvider: String = "Groq",
    val activeModel: String = "llama-3.3-70b-versatile",
    val isWebSocketConnected: Boolean = true,
    val isAccessibilityEnabled: Boolean = true,
    val lastUtterance: String = "",
    val lastResponse: String = "Ready — listening for 'Jarvis'"
)
