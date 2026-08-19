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

    @Test
    fun testWhatsAppCommandResolution() {
        val intent = resolver.resolve("send whatsapp to Bob Hello there")
        assertTrue(intent is JarvisIntent.SendWhatsApp)
        val wa = intent as JarvisIntent.SendWhatsApp
        assertEquals("Bob", wa.contactName)
        assertEquals("Hello there", wa.message)
    }

    @Test
    fun testHinglishTorchCommandResolution() {
        val intent = resolver.resolve("torch chalo")
        assertTrue(intent is JarvisIntent.ToggleTorch)
        assertEquals("on", (intent as JarvisIntent.ToggleTorch).state)
    }
}
