package com.jarvis.assistant.voice

import java.util.UUID

data class VoiceSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val startTimeMs: Long = System.currentTimeMillis()
)
