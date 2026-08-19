package com.jarvis.assistant.llm

data class ModelInfo(
    val id: String,
    val name: String,
    val provider: String,
    val contextLength: Int = 4096,
    val supportsStreaming: Boolean = true
)

data class ProviderConfig(
    val providerId: String,
    val name: String,
    val isAuthenticated: Boolean,
    val activeModelId: String? = null
)

class ProviderRegistry {
    private val availableModels = mutableListOf<ModelInfo>()

    fun updateModels(models: List<ModelInfo>) {
        availableModels.clear()
        availableModels.addAll(models)
    }

    fun getModelsForProvider(providerId: String): List<ModelInfo> {
        return availableModels.filter { it.provider == providerId }
    }
}


