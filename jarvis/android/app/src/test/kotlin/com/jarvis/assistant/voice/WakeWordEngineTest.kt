package com.jarvis.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordEngineTest {

    private class FakeDetector : WakeWordDetector {
        var wakeListener: WakeWordListener? = null
        var started = false
        var paused = false
        var resumed = false
        var released = false

        override fun start() { started = true }
        override fun stop() {}
        override fun pause() { paused = true }
        override fun resume() { resumed = true }
        override fun release() { released = true }
        override fun setListener(listener: WakeWordListener) { this.wakeListener = listener }
        override fun isAvailable() = true
    }

    private val engine = WakeWordEngine(config = WakeWordConfig(cooldownMs = 0L))

    @Test
    fun testWakeWordPhraseVariantsMatch() {
        assertTrue(engine.isWakePhraseMatch("Jarvis"))
        assertTrue(engine.isWakePhraseMatch("Hey Jarvis"))
        assertTrue(engine.isWakePhraseMatch("Jarvis suno"))
        assertFalse(engine.isWakePhraseMatch("random text"))
    }

    @Test
    fun testExtractCommandStripsWakePhrase() {
        assertEquals("Open youtube", engine.extractCommand("Hey Jarvis open YouTube"))
        assertEquals("Open youtube", engine.extractCommand("jarvis open YouTube"))
        assertEquals("", engine.extractCommand("Jarvis"))
    }

    @Test
    fun `wake fires exactly once despite duplicate detector events`() {
        val detector = FakeDetector()
        val engine = WakeWordEngine(
            config = WakeWordConfig(cooldownMs = 60_000),
            detector = detector
        )
        var wakeCount = 0
        engine.startMonitoring(onWake = { wakeCount++ })

        assertTrue(detector.started)
        detector.wakeListener?.onWakeWordDetected()
        detector.wakeListener?.onWakeWordDetected() // duplicate within cooldown
        detector.wakeListener?.onWakeWordDetected()

        assertEquals(1, wakeCount)
    }

    @Test
    fun `pause and resume hand the microphone over`() {
        val detector = FakeDetector()
        val engine = WakeWordEngine(detector = detector)
        engine.startMonitoring(onWake = {})
        engine.pause()
        assertTrue(detector.paused)
        engine.resume()
        assertTrue(detector.resumed)
    }

    @Test
    fun `release stops everything`() {
        val detector = FakeDetector()
        val engine = WakeWordEngine(detector = detector)
        engine.startMonitoring(onWake = {})
        engine.release()
        assertTrue(detector.released)
        assertFalse(engine.isMonitoring())
    }

    @Test
    fun `busy recognizer gets a longer restart backoff`() {
        val engine = WakeWordEngine(config = WakeWordConfig(cooldownMs = 0L))
        val busy = android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY
        val noMatch = android.speech.SpeechRecognizer.ERROR_NO_MATCH
        assertTrue(engine.fallbackRestartDelayMs(busy) > engine.fallbackRestartDelayMs(noMatch))
        assertEquals(engine.fallbackRestartDelayMs(), engine.fallbackRestartDelayMs(noMatch))
    }
}