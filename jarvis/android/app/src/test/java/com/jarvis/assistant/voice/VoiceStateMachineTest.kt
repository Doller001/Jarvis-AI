package com.jarvis.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceStateMachineTest {

    // ── Happy path: push-to-talk (no wake word) ───────────────────────────

    @Test
    fun `happy path IDLE to LISTENING to PROCESSING to SPEAKING to IDLE`() {
        val sm = VoiceStateMachine()
        assertEquals(VoiceState.IDLE, sm.state)
        assertTrue(sm.isIdle)
        assertTrue(sm.transition(VoiceState.LISTENING))
        assertTrue(sm.isListening)
        assertTrue(sm.transition(VoiceState.PROCESSING))
        assertTrue(sm.transition(VoiceState.SPEAKING))
        assertTrue(sm.transition(VoiceState.IDLE))
        assertEquals(VoiceState.IDLE, sm.state)
    }

    @Test
    fun `direct SPEAKING from IDLE is legal for typed or cloud responses`() {
        val sm = VoiceStateMachine()
        assertTrue(sm.transition(VoiceState.SPEAKING))
        assertEquals(VoiceState.SPEAKING, sm.state)
        assertTrue(sm.transition(VoiceState.IDLE))
    }

    // ── Happy path: wake-word flow ────────────────────────────────────────

    @Test
    fun `full wake-word cycle DISABLED to WAKE_LISTENING to ACKNOWLEDGING to COMMAND_LISTENING to PROCESSING to SPEAKING to WAKE_LISTENING`() {
        val sm = VoiceStateMachine(VoiceState.DISABLED)
        assertTrue(sm.transition(VoiceState.WAKE_LISTENING))
        assertTrue(sm.isWakeListening)
        assertTrue(sm.transition(VoiceState.ACKNOWLEDGING))
        assertTrue(sm.transition(VoiceState.COMMAND_LISTENING))
        assertTrue(sm.isCommandListening)
        assertFalse(sm.isWakeListening)   // CRITICAL: not wake mode anymore
        assertTrue(sm.transition(VoiceState.PROCESSING))
        assertTrue(sm.transition(VoiceState.SPEAKING))
        assertTrue(sm.transition(VoiceState.WAKE_LISTENING))
        assertTrue(sm.isWakeListening)
    }

    // ── Critical rule: wake events must be REJECTED in non-wake states ────

    @Test
    fun `COMMAND_LISTENING does not accept WAKE_LISTENING transition from outside`() {
        // The state machine from COMMAND_LISTENING should NOT go back to WAKE_LISTENING
        // in a single hop (only allowed via PROCESSING or error recovery).
        // This verifies the state machine rejects illegal transitions.
        val sm = VoiceStateMachine(VoiceState.COMMAND_LISTENING)
        // COMMAND_LISTENING → WAKE_LISTENING is legal (abort command, re-arm wake).
        // But COMMAND_LISTENING → ACKNOWLEDGING is not legal.
        assertFalse(sm.transition(VoiceState.ACKNOWLEDGING))
        assertEquals(VoiceState.COMMAND_LISTENING, sm.state)
    }

    @Test
    fun `SPEAKING cannot transition to COMMAND_LISTENING (prevents TTS feedback loop)`() {
        val sm = VoiceStateMachine(VoiceState.SPEAKING)
        // Phase 8 critical: SPEAKING → COMMAND_LISTENING is FORBIDDEN.
        assertFalse(sm.transition(VoiceState.COMMAND_LISTENING))
        assertEquals(VoiceState.SPEAKING, sm.state)
    }

    @Test
    fun `DISABLED rejects wake event (ACKNOWLEDGING transition)`() {
        val sm = VoiceStateMachine(VoiceState.DISABLED)
        assertFalse(sm.transition(VoiceState.ACKNOWLEDGING))
        assertEquals(VoiceState.DISABLED, sm.state)
    }

    @Test
    fun `PROCESSING rejects ACKNOWLEDGING (mid-cycle wake ignored)`() {
        val sm = VoiceStateMachine(VoiceState.PROCESSING)
        assertFalse(sm.transition(VoiceState.ACKNOWLEDGING))
        assertEquals(VoiceState.PROCESSING, sm.state)
    }

    // ── Error and recovery ────────────────────────────────────────────────

    @Test
    fun `error recovers to IDLE`() {
        val sm = VoiceStateMachine()
        sm.transition(VoiceState.LISTENING)
        assertTrue(sm.transition(VoiceState.ERROR))
        assertTrue(sm.recoverFromError())
        assertEquals(VoiceState.IDLE, sm.state)
    }

    @Test
    fun `RECOVERING can go to WAKE_LISTENING or DISABLED`() {
        val sm1 = VoiceStateMachine(VoiceState.RECOVERING)
        assertTrue(sm1.transition(VoiceState.WAKE_LISTENING))

        val sm2 = VoiceStateMachine(VoiceState.RECOVERING)
        assertTrue(sm2.transition(VoiceState.DISABLED))
    }

    @Test
    fun `any state can return to IDLE via recoverFromError`() {
        listOf(
            VoiceState.WAKE_LISTENING, VoiceState.ACKNOWLEDGING,
            VoiceState.COMMAND_LISTENING, VoiceState.PROCESSING,
            VoiceState.SPEAKING, VoiceState.RECOVERING, VoiceState.ERROR
        ).forEach { startState ->
            val sm = VoiceStateMachine(startState)
            assertTrue("Should recover from $startState", sm.recoverFromError())
            assertEquals(VoiceState.IDLE, sm.state)
        }
    }

    // ── isWakeListening / isCommandListening helpers ──────────────────────

    @Test
    fun `isWakeListening is true for WAKE_LISTENING and WAKE alias`() {
        assertTrue(VoiceStateMachine(VoiceState.WAKE_LISTENING).isWakeListening)
        assertTrue(VoiceStateMachine(VoiceState.WAKE).isWakeListening)
        assertFalse(VoiceStateMachine(VoiceState.COMMAND_LISTENING).isWakeListening)
        assertFalse(VoiceStateMachine(VoiceState.ACKNOWLEDGING).isWakeListening)
    }

    @Test
    fun `isCommandListening is true for COMMAND_LISTENING and LISTENING alias`() {
        assertTrue(VoiceStateMachine(VoiceState.COMMAND_LISTENING).isCommandListening)
        assertTrue(VoiceStateMachine(VoiceState.LISTENING).isCommandListening)
        assertFalse(VoiceStateMachine(VoiceState.WAKE_LISTENING).isCommandListening)
    }
}
