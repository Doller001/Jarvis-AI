package com.jarvis.assistant.voice

object InterruptVocabulary {

    data class Phrase(val text: String, val language: String)

    private val PHRASES: List<Phrase> = listOf(
        // English
        Phrase("stop", "en"),
        Phrase("mute", "en"),
        Phrase("pause", "en"),
        Phrase("quiet", "en"),
        Phrase("be quiet", "en"),
        Phrase("stop talking", "en"),
        Phrase("don't speak", "en"),
        Phrase("don't talk", "en"),
        Phrase("cancel", "en"),
        Phrase("shut up", "en"),
        Phrase("enough", "en"),
        Phrase("that's enough", "en"),
        Phrase("wait", "en"),

        // Hindi
        Phrase("ruko", "hi"),
        Phrase("rukko", "hi"),
        Phrase("ruk jao", "hi"),
        Phrase("bas", "hi"),
        Phrase("bas karo", "hi"),
        Phrase("chup", "hi"),
        Phrase("chup karo", "hi"),
        Phrase("chup raho", "hi"),
        Phrase("band", "hi"),
        Phrase("band karo", "hi"),
        Phrase("suno", "hi"),
        Phrase("sun", "hi"),
        Phrase("arre ruko", "hi"),
        Phrase("arre bas", "hi"),
        Phrase("theek hai", "hi"),
    )

    private val ENGLISH_SET: Set<String> = PHRASES
        .filter { it.language == "en" }
        .map { it.text }
        .toSet()

    private val HINDI_SET: Set<String> = PHRASES
        .filter { it.language == "hi" }
        .map { it.text }
        .toSet()

    val allEnglish: Set<String> get() = ENGLISH_SET
    val allHindi: Set<String> get() = HINDI_SET

    fun containsEnglish(text: String): Boolean {
        val lower = text.lowercase().trim()
        return ENGLISH_SET.any { lower.contains(it) }
    }

    fun containsHindi(text: String): Boolean {
        val lower = text.lowercase().trim()
        return HINDI_SET.any { lower.contains(it) }
    }

    fun isInterrupt(text: String): Boolean {
        return containsEnglish(text) || containsHindi(text)
    }
}
