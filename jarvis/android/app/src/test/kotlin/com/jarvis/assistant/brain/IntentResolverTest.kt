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

    @Test
    fun testMediaControlResolution() {
        val play = resolver.resolve("Jarvis gaana bajao")
        assertTrue(play is JarvisIntent.MediaControl)
        assertEquals("play", (play as JarvisIntent.MediaControl).action)

        val pause = resolver.resolve("music pause")
        assertTrue(pause is JarvisIntent.MediaControl)
        assertEquals("pause", (pause as JarvisIntent.MediaControl).action)

        val next = resolver.resolve("agla gaana")
        assertTrue(next is JarvisIntent.MediaControl)
        assertEquals("next", (next as JarvisIntent.MediaControl).action)

        val prev = resolver.resolve("previous song")
        assertTrue(prev is JarvisIntent.MediaControl)
        assertEquals("prev", (prev as JarvisIntent.MediaControl).action)
    }

    @Test
    fun testBluetoothAndVolumeResolution() {
        val bt = resolver.resolve("bluetooth chalo")
        assertTrue(bt is JarvisIntent.ToggleBluetooth)
        assertEquals("on", (bt as JarvisIntent.ToggleBluetooth).state)

        val mute = resolver.resolve("chup")
        assertTrue(mute is JarvisIntent.SetVolume)
        assertEquals(0, (mute as JarvisIntent.SetVolume).level)
    }

    @Test
    fun testOpenAppResolution() {
        val app = resolver.resolve("youtube kholo")
        assertTrue(app is JarvisIntent.OpenApp)
        assertEquals("youtube", (app as JarvisIntent.OpenApp).appName)
    }

    @Test
    fun testAppDiscoveryResolution() {
        val allApps = resolver.resolve("apps list")
        assertTrue(allApps is JarvisIntent.ListApps)
        assertNull((allApps as JarvisIntent.ListApps).category)

        val musicApps = resolver.resolve("music apps")
        assertTrue(musicApps is JarvisIntent.ListApps)
        assertEquals("music", (musicApps as JarvisIntent.ListApps).category)

        val gameApps = resolver.resolve("game apps")
        assertTrue(gameApps is JarvisIntent.ListApps)
        assertEquals("games", (gameApps as JarvisIntent.ListApps).category)
    }

    @Test
    fun testGalleryStorageAndCallLogResolution() {
        val gallery = resolver.resolve("photo dikhao")
        assertTrue(gallery is JarvisIntent.OpenGallery)

        val storage = resolver.resolve("check phone storage")
        assertTrue(storage is JarvisIntent.GetStorage)

        val callLog = resolver.resolve("recent calls")
        assertTrue(callLog is JarvisIntent.GetCallLog)

        val settings = resolver.resolve("open settings")
        assertTrue(settings is JarvisIntent.OpenSettings)
    }
}
