package com.jarvis.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VoiceStateMachineTest {

    private lateinit var sm: VoiceStateMachine

    @Before
    fun setup() {
        sm = VoiceStateMachine()
    }

    @Test
    fun `initial state is DISABLED`() {
        assertEquals(VoiceState.DISABLED, sm.state)
    }

    @Test
    fun `DISABLED can transition to WAKE_LISTENING`() {
        assertTrue(sm.transition(VoiceState.WAKE_LISTENING))
        assertEquals(VoiceState.WAKE_LISTENING, sm.state)
    }

    @Test
    fun `WAKE_LISTENING can transition to ACKNOWLEDGING`() {
        sm.transition(VoiceState.WAKE_LISTENING)
        assertTrue(sm.transition(VoiceState.ACKNOWLEDGING))
        assertEquals(VoiceState.ACKNOWLEDGING, sm.state)
    }

    @Test
    fun `WAKE_LISTENING can transition directly to COMMAND_LISTENING for manual commands`() {
        sm.transition(VoiceState.WAKE_LISTENING)
        assertTrue(sm.transition(VoiceState.COMMAND_LISTENING))
        assertEquals(VoiceState.COMMAND_LISTENING, sm.state)
    }

    @Test
    fun `ACKNOWLEDGING can transition to COMMAND_LISTENING`() {
        sm.transition(VoiceState.WAKE_LISTENING)
        sm.transition(VoiceState.ACKNOWLEDGING)
        assertTrue(sm.transition(VoiceState.COMMAND_LISTENING))
        assertEquals(VoiceState.COMMAND_LISTENING, sm.state)
    }

    @Test
    fun `COMMAND_LISTENING can transition to PROCESSING`() {
        sm.transition(VoiceState.WAKE_LISTENING)
        sm.transition(VoiceState.ACKNOWLEDGING)
        sm.transition(VoiceState.COMMAND_LISTENING)
        assertTrue(sm.transition(VoiceState.PROCESSING))
        assertEquals(VoiceState.PROCESSING, sm.state)
    }

    @Test
    fun `PROCESSING can transition to SPEAKING`() {
        sm.transition(VoiceState.WAKE_LISTENING)
        sm.transition(VoiceState.ACKNOWLEDGING)
        sm.transition(VoiceState.COMMAND_LISTENING)
        sm.transition(VoiceState.PROCESSING)
        assertTrue(sm.transition(VoiceState.SPEAKING))
        assertEquals(VoiceState.SPEAKING, sm.state)
    }

    @Test
    fun `SPEAKING can transition to INTERRUPTING`() {
        sm.forceState(VoiceState.SPEAKING)
        assertTrue(sm.transition(VoiceState.INTERRUPTING))
        assertEquals(VoiceState.INTERRUPTING, sm.state)
    }

    @Test
    fun `INTERRUPTING can transition to COMMAND_LISTENING`() {
        sm.forceState(VoiceState.INTERRUPTING)
        assertTrue(sm.transition(VoiceState.COMMAND_LISTENING))
        assertEquals(VoiceState.COMMAND_LISTENING, sm.state)
    }

    @Test
    fun `SPEAKING can transition to WAKE_LISTENING`() {
        sm.forceState(VoiceState.SPEAKING)
        assertTrue(sm.transition(VoiceState.WAKE_LISTENING))
        assertEquals(VoiceState.WAKE_LISTENING, sm.state)
    }

    @Test
    fun `WAKE_LISTENING can transition to DISABLED`() {
        sm.transition(VoiceState.WAKE_LISTENING)
        assertTrue(sm.transition(VoiceState.DISABLED))
        assertEquals(VoiceState.DISABLED, sm.state)
    }

    @Test
    fun `error state transitions to RECOVERING then WAKE_LISTENING`() {
        sm.forceState(VoiceState.COMMAND_LISTENING)
        assertTrue(sm.transition(VoiceState.RECOVERING))
        assertEquals(VoiceState.RECOVERING, sm.state)
        assertTrue(sm.transition(VoiceState.WAKE_LISTENING))
        assertEquals(VoiceState.WAKE_LISTENING, sm.state)
    }

    @Test
    fun `forceState bypasses transition table`() {
        sm.forceState(VoiceState.SPEAKING)
        assertEquals(VoiceState.SPEAKING, sm.state)
        sm.forceState(VoiceState.INTERRUPTING)
        assertEquals(VoiceState.INTERRUPTING, sm.state)
    }

    @Test
    fun `recoverTo bypasses transition table`() {
        sm.forceState(VoiceState.SPEAKING)
        assertTrue(sm.recoverTo(VoiceState.WAKE_LISTENING))
        assertEquals(VoiceState.WAKE_LISTENING, sm.state)
    }

    @Test
    fun `isWakeListening helper`() {
        assertTrue(VoiceStateMachine(VoiceState.WAKE_LISTENING).isWakeListening)
        assertFalse(VoiceStateMachine(VoiceState.COMMAND_LISTENING).isWakeListening)
    }

    @Test
    fun `isCommandListening helper`() {
        assertTrue(VoiceStateMachine(VoiceState.COMMAND_LISTENING).isCommandListening)
        assertFalse(VoiceStateMachine(VoiceState.WAKE_LISTENING).isCommandListening)
    }

    @Test
    fun `full happy path cycle`() {
        assertEquals(VoiceState.DISABLED, sm.state)
        sm.transition(VoiceState.WAKE_LISTENING)
        assertEquals(VoiceState.WAKE_LISTENING, sm.state)
        sm.transition(VoiceState.ACKNOWLEDGING)
        assertEquals(VoiceState.ACKNOWLEDGING, sm.state)
        sm.transition(VoiceState.COMMAND_LISTENING)
        assertEquals(VoiceState.COMMAND_LISTENING, sm.state)
        sm.transition(VoiceState.PROCESSING)
        assertEquals(VoiceState.PROCESSING, sm.state)
        sm.transition(VoiceState.SPEAKING)
        assertEquals(VoiceState.SPEAKING, sm.state)
        sm.transition(VoiceState.WAKE_LISTENING)
        assertEquals(VoiceState.WAKE_LISTENING, sm.state)
    }

    @Test
    fun `interrupt path`() {
        sm.forceState(VoiceState.SPEAKING)
        sm.transition(VoiceState.INTERRUPTING)
        assertEquals(VoiceState.INTERRUPTING, sm.state)
        sm.transition(VoiceState.COMMAND_LISTENING)
        assertEquals(VoiceState.COMMAND_LISTENING, sm.state)
    }
}
