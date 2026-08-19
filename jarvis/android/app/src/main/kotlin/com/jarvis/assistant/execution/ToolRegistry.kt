package com.jarvis.assistant.execution

data class JarvisTool(
    val name: String = "",
    val description: String,
    val requiresConfirmation: Boolean = false,
    val riskLevel: String = "safe"
)

class ToolRegistry {
    private val tools = mutableMapOf<String, JarvisTool>()

    init {
        register(JarvisTool("toggle_torch", "Toggle camera flashlight"))
        register(JarvisTool("toggle_wifi", "Toggle Wi-Fi state"))
        register(JarvisTool("call_contact", "Initiate phone call", requiresConfirmation = true, riskLevel = "confirmation"))
    }

    fun register(tool: JarvisTool) {
        tools[tool.name] = tool
    }

    fun getTool(name: String): JarvisTool? = tools[name]
}
