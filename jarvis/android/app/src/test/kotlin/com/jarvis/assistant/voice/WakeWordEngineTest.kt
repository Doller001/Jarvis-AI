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

    @Test
    fun `silence errors reuse the recognizer, real failures recreate it`() {
        val engine = WakeWordEngine(config = WakeWordConfig(cooldownMs = 0L))
        assertFalse(engine.shouldRecreateOnError(android.speech.SpeechRecognizer.ERROR_NO_MATCH))
        assertFalse(engine.shouldRecreateOnError(android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
        assertTrue(engine.shouldRecreateOnError(android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
        assertTrue(engine.shouldRecreateOnError(android.speech.SpeechRecognizer.ERROR_CLIENT))
        assertTrue(engine.shouldRecreateOnError(android.speech.SpeechRecognizer.ERROR_SERVER))
    }

    @Test
    fun `utterance accumulates across recognizer restarts`() {
        val engine = WakeWordEngine(config = WakeWordConfig(cooldownMs = 0L))
        val first = engine.accumulate("hey jarvis what")
        assertEquals("hey jarvis what", first)
        val second = engine.accumulate("hey jarvis what is 2 plus 2")
        assertEquals("hey jarvis what is 2 plus 2", second)
        val third = engine.accumulate("hey jarvis what is 2 plus 2?")
        assertTrue(third.startsWith("hey jarvis what is 2 plus 2"))
    }

    @Test
    fun `disjoint fragment appends to utterance`() {
        val engine = WakeWordEngine(config = WakeWordConfig(cooldownMs = 0L))
        engine.accumulate("hey jarvis")
        assertEquals("hey jarvis open youtube", engine.accumulate("open youtube"))
    }
}