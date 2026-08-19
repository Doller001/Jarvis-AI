package com.jarvis.assistant.brain

import org.junit.Assert.*
import org.junit.Test

class IntentResolverTest {
    private val resolver = IntentResolver()

    @Test
    fun testTorchCommandResolution() {
        val intent = resolver.resolve("Jarvis torch on")
        assertTrue(intent is JarvisIntent.ToggleTorch)
        assertEquals("on", (intent as JarvisIntent.ToggleTorch).state)
    }

    @Test
    fun testTimeCommandResolution() {
        val intent = resolver.resolve("Jarvis time kya hai")
        assertTrue(intent is JarvisIntent.GetTime)
    }

    @Test
    fun testRiskyCallCommandResolution() {
        val intent = resolver.resolve("call Alice")
        assertTrue(intent is JarvisIntent.CallContact)
        assertEquals("Alice", (intent as JarvisIntent.CallContact).contactName)
    }
}
