package com.jarvis.assistant.voice

import org.junit.Assert.*
import org.junit.Test

class WakeWordEngineTest {
    private val engine = WakeWordEngine(cooldownMs = 0L)

    @Test
    fun testWakeWordPhraseVariantsMatch() {
        assertTrue(engine.isWakePhraseMatch("Jarvis"))
        assertTrue(engine.isWakePhraseMatch("Hey Jarvis"))
        assertTrue(engine.isWakePhraseMatch("Jarvis suno"))
        assertFalse(engine.isWakePhraseMatch("random text"))
    }
}
