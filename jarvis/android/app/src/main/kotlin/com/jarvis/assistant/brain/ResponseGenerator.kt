package com.jarvis.assistant.brain

class ResponseGenerator {
    fun generateSpokenResponse(intent: JarvisIntent, resultMessage: String): String {
        return resultMessage.ifBlank { "Command executed, Sir." }
    }
}
