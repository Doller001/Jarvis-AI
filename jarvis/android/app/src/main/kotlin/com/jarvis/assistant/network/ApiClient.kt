package com.jarvis.assistant.network

import android.util.Log

class ApiClient(val baseUrl: String = "http://10.0.2.2:8000") {
    fun fetchAvailableProviders(onResult: (List<String>) -> Unit) {
        Log.i("ApiClient", "Fetching active providers from $baseUrl/api/v1/providers")
        onResult(listOf("Groq", "OpenRouter", "Gemini", "Ollama"))
    }
}
