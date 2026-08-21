package com.jarvis.assistant.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryStoreTest {

    @Test
    fun testRecordAndClearHistory() {
        val memoryStore = MemoryStore()
        assertTrue(memoryStore.getHistory().isEmpty())

        memoryStore.recordUserMessage("Hey Jarvis")
        memoryStore.recordAssistantMessage("Hello Saif, how can I help you?")

        val history = memoryStore.getHistory()
        assertEquals(2, history.size)
        assertEquals("user", history[0].role)
        assertEquals("Hey Jarvis", history[0].text)
        assertEquals("assistant", history[1].role)

        memoryStore.clearHistory()
        assertTrue(memoryStore.getHistory().isEmpty())
    }
}
