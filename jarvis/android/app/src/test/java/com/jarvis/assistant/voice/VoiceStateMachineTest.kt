package com.jarvis.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceStateMachineTest {

    @Test
    fun `happy path follows the full push-to-talk cycle`() {
        val sm = VoiceStateMachine()
        assertEquals(VoiceState.STOPPED, sm.state)
        assertTrue(sm.transition(VoiceState.STARTING))
        assertTrue(sm.transition(VoiceState.COMMAND_LISTENING))
        assertTrue(sm.transition(VoiceState.PROCESSING))
        assertTrue(sm.transition(VoiceState.SPEAKING))
        assertTrue(sm.transition(VoiceState.STOPPED))
        assertEquals(VoiceState.STOPPED, sm.state)
    }

    @Test
    fun `direct command listening from stopped is legal`() {
        val sm = VoiceStateMachine()
        assertTrue(sm.transition(VoiceState.COMMAND_LISTENING))
        assertEquals(VoiceState.COMMAND_LISTENING, sm.state)
        assertTrue(sm.isListening)
    }

    @Test
    fun `illegal transitions are rejected`() {
        val sm = VoiceStateMachine()
        assertFalse(sm.transition(VoiceState.SPEAKING)) // STOPPED -> SPEAKING
        sm.transition(VoiceState.STARTING)
        assertFalse(sm.transition(VoiceState.SPEAKING)) // STARTING -> SPEAKING
        sm.transition(VoiceState.COMMAND_LISTENING)
        assertTrue(sm.transition(VoiceState.STOPPED)) // legal stop
    }

    @Test
    fun `error recovers to stopped`() {
        val sm = VoiceStateMachine()
        sm.transition(VoiceState.COMMAND_LISTENING)
        assertTrue(sm.transition(VoiceState.ERROR))
        assertEquals(VoiceState.ERROR, sm.state)
        assertTrue(sm.recoverFromError())
        assertEquals(VoiceState.STOPPED, sm.state)
    }

    @Test
    fun `any state can stop`() {
        val sm = VoiceStateMachine()
        sm.transition(VoiceState.STARTING)
        sm.transition(VoiceState.COMMAND_LISTENING)
        sm.transition(VoiceState.PROCESSING)
        assertTrue(sm.transition(VoiceState.STOPPED))
    }
}