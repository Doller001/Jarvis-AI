package com.jarvis.assistant.llm

import android.util.Log

class ProviderManager(
    private val registry: ProviderRegistry = ProviderRegistry()
) {
    var activeProvider: String = "NVIDIA"
        private set
    var activeModel: String = "nvidia/nemotron-3.5-lightning-30b-a3b"
        private set

    private val supportedProviders = mutableMapOf(
        "NVIDIA" to listOf("nvidia/nemotron-3.5-lightning-30b-a3b", "nvidia/llama-3.1-nemotron-70b-instruct", "nvidia/nemotron-4-340b-instruct"),
        "Groq" to listOf("groq/compound", "groq/compound-mini", "llama-3.1-8b-instant"),
        "OpenRouter" to listOf("anthropic/claude-3.5-sonnet", "openai/gpt-4o-mini", "google/gemini-2.0-flash-exp:free"),
        "Gemini" to listOf("gemini-2.0-flash", "gemini-1.5-pro", "gemini-1.5-flash"),
        "Ollama" to listOf("llama3.2:3b", "mistral:7b", "qwen2.5:7b")
    )

    fun selectProviderAndModel(provider: String, model: String) {
        activeProvider = provider
        activeModel = model
        Log.i("ProviderManager", "Active LLM set to provider='$provider', model='$model'")
    }

    fun getAvailableProviders(): List<String> = supportedProviders.keys.toList()

    fun getModelsForProvider(provider: String): List<String> {
        return supportedProviders[provider] ?: listOf(activeModel)
    }

    fun isProviderAvailable(provider: String): Boolean {
        return supportedProviders.containsKey(provider)
    }

    fun getActiveConfig(): ProviderConfig {
        return ProviderConfig(
            providerId = activeProvider,
            name = activeProvider,
            isAuthenticated = true,
            activeModelId = activeModel
        )
    }
}
