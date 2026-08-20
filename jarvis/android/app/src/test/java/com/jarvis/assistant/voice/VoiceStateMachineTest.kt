package com.jarvis.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceStateMachineTest {

    @Test
    fun `happy path follows the full cycle`() {
        val sm = VoiceStateMachine()
        assertTrue(sm.transition(VoiceState.STARTING))
        assertTrue(sm.transition(VoiceState.WAKE_LISTENING))
        assertTrue(sm.transition(VoiceState.WAKE_DETECTED))
        assertTrue(sm.transition(VoiceState.COMMAND_LISTENING))
        assertTrue(sm.transition(VoiceState.PROCESSING))
        assertTrue(sm.transition(VoiceState.SPEAKING))
        assertTrue(sm.transition(VoiceState.WAKE_LISTENING))
        assertEquals(VoiceState.WAKE_LISTENING, sm.state)
    }

    @Test
    fun `illegal transitions are rejected`() {
        val sm = VoiceStateMachine()
        assertFalse(sm.transition(VoiceState.SPEAKING)) // STOPPED -> SPEAKING
        sm.transition(VoiceState.STARTING)
        assertFalse(sm.transition(VoiceState.COMMAND_LISTENING)) // STARTING -> COMMAND_LISTENING
        sm.transition(VoiceState.WAKE_LISTENING)
        assertTrue(sm.transition(VoiceState.STOPPED)) // legal stop
    }

    @Test
    fun `error recovers to wake listening`() {
        val sm = VoiceStateMachine()
        sm.transition(VoiceState.STARTING)
        sm.transition(VoiceState.WAKE_LISTENING)
        sm.transition(VoiceState.WAKE_DETECTED)
        sm.transition(VoiceState.COMMAND_LISTENING)
        assertTrue(sm.transition(VoiceState.ERROR))
        assertEquals(VoiceState.ERROR, sm.state)
        assertTrue(sm.recoverFromError())
        assertEquals(VoiceState.WAKE_LISTENING, sm.state)
    }

    @Test
    fun `any state can stop`() {
        val sm = VoiceStateMachine()
        sm.transition(VoiceState.STARTING)
        sm.transition(VoiceState.WAKE_LISTENING)
        sm.transition(VoiceState.WAKE_DETECTED)
        sm.transition(VoiceState.COMMAND_LISTENING)
        sm.transition(VoiceState.PROCESSING)
        assertTrue(sm.transition(VoiceState.STOPPED))
    }

    @Test
    fun `late async response can speak from wake listening`() {
        val sm = VoiceStateMachine()
        sm.transition(VoiceState.STARTING)
        sm.transition(VoiceState.WAKE_LISTENING)
        assertTrue(sm.transition(VoiceState.SPEAKING))
        assertTrue(sm.transition(VoiceState.WAKE_LISTENING))
    }
}

class WakeCooldownTest {

    @Test
    fun `duplicate wake events are suppressed within cooldown`() {
        val cooldown = WakeCooldown(cooldownMs = 1500)
        assertTrue(cooldown.allow())
        assertFalse(cooldown.allow())
        assertFalse(cooldown.allow())
    }

    @Test
    fun `wake is allowed again after cooldown window`() {
        val cooldown = WakeCooldown(cooldownMs = 1)
        assertTrue(cooldown.allow())
        assertFalse(cooldown.allow())
        Thread.sleep(5)
        assertTrue(cooldown.allow())
    }

    @Test
    fun `reset clears the gate`() {
        val cooldown = WakeCooldown(cooldownMs = 60_000)
        assertTrue(cooldown.allow())
        assertFalse(cooldown.allow())
        cooldown.reset()
        assertTrue(cooldown.allow())
    }
}

class WakeWordConfigTest {

    @Test
    fun `defaults are sane`() {
        val c = WakeWordConfig()
        assertTrue(c.enabled)
        assertTrue(c.sensitivity in 0f..1f)
        assertTrue(c.cooldownMs > 0)
        assertTrue(c.commandTimeoutMs > 0)
        assertTrue(c.fallbackTextMatchingEnabled)
    }
}