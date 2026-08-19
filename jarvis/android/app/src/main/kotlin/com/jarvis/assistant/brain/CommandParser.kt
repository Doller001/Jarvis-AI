package com.jarvis.assistant.brain

class CommandParser {
    private val resolver = IntentResolver()

    fun parse(text: String): JarvisIntent {
        return resolver.resolve(text)
    }
}
