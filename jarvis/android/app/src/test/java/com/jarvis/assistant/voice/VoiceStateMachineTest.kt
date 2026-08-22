package com.jarvis.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceStateMachineTest {

    @Test
    fun `happy path follows the full cycle IDLE to LISTENING to PROCESSING to SPEAKING to IDLE`() {
        val sm = VoiceStateMachine()
        assertEquals(VoiceState.IDLE, sm.state)
        assertTrue(sm.isIdle)
        assertTrue(sm.transition(VoiceState.LISTENING))
        assertTrue(sm.isListening)
        assertTrue(sm.transition(VoiceState.PROCESSING))
        assertTrue(sm.transition(VoiceState.SPEAKING))
        assertTrue(sm.transition(VoiceState.IDLE))
        assertEquals(VoiceState.IDLE, sm.state)
        assertTrue(sm.isIdle)
    }

    @Test
    fun `direct listening from IDLE is legal`() {
        val sm = VoiceStateMachine()
        assertTrue(sm.transition(VoiceState.LISTENING))
        assertEquals(VoiceState.LISTENING, sm.state)
        assertTrue(sm.isListening)
    }

    @Test
    fun `illegal transitions are rejected`() {
        val sm = VoiceStateMachine()
        assertFalse(sm.transition(VoiceState.SPEAKING)) // IDLE -> SPEAKING is illegal
        sm.transition(VoiceState.LISTENING)
        assertFalse(sm.transition(VoiceState.SPEAKING)) // LISTENING -> SPEAKING without processing is illegal
        assertTrue(sm.transition(VoiceState.IDLE))      // LISTENING -> IDLE cancellation is legal
    }

    @Test
    fun `error recovers to IDLE`() {
        val sm = VoiceStateMachine()
        sm.transition(VoiceState.LISTENING)
        assertTrue(sm.transition(VoiceState.ERROR))
        assertEquals(VoiceState.ERROR, sm.state)
        assertTrue(sm.recoverFromError())
        assertEquals(VoiceState.IDLE, sm.state)
    }

    @Test
    fun `any state can return to IDLE`() {
        val sm = VoiceStateMachine()
        sm.transition(VoiceState.LISTENING)
        sm.transition(VoiceState.PROCESSING)
        assertTrue(sm.transition(VoiceState.IDLE))
    }

    @Test
    fun `speaking can transition to listening directly on user barge-in`() {
        val sm = VoiceStateMachine()
        sm.transition(VoiceState.LISTENING)
        sm.transition(VoiceState.PROCESSING)
        sm.transition(VoiceState.SPEAKING)
        assertTrue(sm.transition(VoiceState.LISTENING))
        assertEquals(VoiceState.LISTENING, sm.state)
    }
}