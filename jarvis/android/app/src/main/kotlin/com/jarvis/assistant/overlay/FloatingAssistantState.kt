package com.jarvis.assistant.overlay

import com.jarvis.assistant.voice.VoiceState

/**
 * Immutable snapshot of everything the floating overlay needs to render.
 * Produced by JarvisOverlayService, consumed by JarvisFloatingOverlay (Compose).
 */
data class FloatingAssistantState(
    val visible: Boolean = false,
    val voiceState: VoiceState = VoiceState.IDLE,
    val wakeEnabled: Boolean = false,
    val userQuery: String = "",
    val response: String = "",
    val isThinking: Boolean = false,
    val requiresConfirmation: Boolean = false,
    val confirmationPrompt: String = ""
)
