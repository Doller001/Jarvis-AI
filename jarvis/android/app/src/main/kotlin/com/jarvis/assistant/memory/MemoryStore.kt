package com.jarvis.assistant.memory

import java.util.concurrent.ConcurrentHashMap

data class MessageLog(val role: String, val text: String, val timestamp: Long = System.currentTimeMillis())

class MemoryStore {
    private val history = mutableListOf<MessageLog>()
    private val preferences = ConcurrentHashMap<String, Any>()

    fun recordUserMessage(text: String) {
        history.add(MessageLog("user", text))
    }

    fun recordAssistantMessage(text: String) {
        history.add(MessageLog("assistant", text))
    }

    fun getHistory(limit: Int = 10): List<MessageLog> {
        return history.takeLast(limit)
    }

    fun setPreference(key: String, value: Any) {
        preferences[key] = value
    }

    fun getPreference(key: String): Any? = preferences[key]
}
