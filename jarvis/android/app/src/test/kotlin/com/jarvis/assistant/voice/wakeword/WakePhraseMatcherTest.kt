package com.jarvis.assistant.voice.wakeword

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakePhraseMatcherTest {

    @Test
    fun matchesExactWakePhrases() {
        assertTrue(WakePhraseMatcher.matches("jarvis"))
        assertTrue(WakePhraseMatcher.matches("hey jarvis"))
        assertTrue(WakePhraseMatcher.matches("Hey Jarvis."))
    }

    @Test
    fun rejectsLongerCommandsAndUnrelatedText() {
        assertFalse(WakePhraseMatcher.matches("hey jarvis open youtube"))
        assertFalse(WakePhraseMatcher.matches("jarvis please open camera"))
        assertFalse(WakePhraseMatcher.matches("hello jarvis assistant"))
        assertFalse(WakePhraseMatcher.matches("yesterday"))
    }
}
