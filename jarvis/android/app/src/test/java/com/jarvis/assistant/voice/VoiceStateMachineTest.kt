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
    fun `DISABLED can transition to WAKE_LISTENING via EnableWake`() {
        assertTrue(sm.dispatch(VoiceEvent.EnableWake))
        assertEquals(VoiceState.WAKE_LISTENING, sm.state)
    }

    @Test
    fun `WAKE_LISTENING can transition to ACKNOWLEDGING via WakeConfirmed`() {
        sm.dispatch(VoiceEvent.EnableWake)
        assertTrue(sm.dispatch(VoiceEvent.WakeConfirmed))
        assertEquals(VoiceState.ACKNOWLEDGING, sm.state)
    }

    @Test
    fun `ACKNOWLEDGING can transition to COMMAND_LISTENING via WakeCaptureReleased`() {
        sm.dispatch(VoiceEvent.EnableWake)
        sm.dispatch(VoiceEvent.WakeConfirmed)
        assertTrue(sm.dispatch(VoiceEvent.WakeCaptureReleased))
        assertEquals(VoiceState.COMMAND_LISTENING, sm.state)
    }

    @Test
    fun `COMMAND_LISTENING can transition to PROCESSING via SpeechFinished`() {
        sm.dispatch(VoiceEvent.EnableWake)
        sm.dispatch(VoiceEvent.WakeConfirmed)
        sm.dispatch(VoiceEvent.WakeCaptureReleased)
        assertTrue(sm.dispatch(VoiceEvent.SpeechFinished))
        assertEquals(VoiceState.PROCESSING, sm.state)
    }

    @Test
    fun `PROCESSING can transition to SPEAKING via TtsStarted`() {
        sm.dispatch(VoiceEvent.EnableWake)
        sm.dispatch(VoiceEvent.WakeConfirmed)
        sm.dispatch(VoiceEvent.WakeCaptureReleased)
        sm.dispatch(VoiceEvent.SpeechFinished)
        assertTrue(sm.dispatch(VoiceEvent.TtsStarted))
        assertEquals(VoiceState.SPEAKING, sm.state)
    }

    @Test
    fun `SPEAKING can transition to INTERRUPTING via InterruptDetected`() {
        sm.dispatch(VoiceEvent.EnableWake)
        sm.dispatch(VoiceEvent.WakeConfirmed)
        sm.dispatch(VoiceEvent.WakeCaptureReleased)
        sm.dispatch(VoiceEvent.SpeechFinished)
        sm.dispatch(VoiceEvent.TtsStarted)
        assertTrue(sm.dispatch(VoiceEvent.InterruptDetected))
        assertEquals(VoiceState.INTERRUPTING, sm.state)
    }

    @Test
    fun `INTERRUPTING can transition to COMMAND_LISTENING via CommandRequested BARGE_IN`() {
        sm.dispatch(VoiceEvent.EnableWake)
        sm.dispatch(VoiceEvent.WakeConfirmed)
        sm.dispatch(VoiceEvent.WakeCaptureReleased)
        sm.dispatch(VoiceEvent.SpeechFinished)
        sm.dispatch(VoiceEvent.TtsStarted)
        sm.dispatch(VoiceEvent.InterruptDetected)
        assertTrue(sm.dispatch(VoiceEvent.CommandRequested(CommandTrigger.BARGE_IN)))
        assertEquals(VoiceState.COMMAND_LISTENING, sm.state)
    }

    @Test
    fun `SPEAKING can transition to WAKE_LISTENING via TtsFinished`() {
        sm.dispatch(VoiceEvent.EnableWake)
        sm.dispatch(VoiceEvent.WakeConfirmed)
        sm.dispatch(VoiceEvent.WakeCaptureReleased)
        sm.dispatch(VoiceEvent.SpeechFinished)
        sm.dispatch(VoiceEvent.TtsStarted)
        assertTrue(sm.dispatch(VoiceEvent.TtsFinished))
        assertEquals(VoiceState.WAKE_LISTENING, sm.state)
    }

    @Test
    fun `DISABLED can transition directly to COMMAND_LISTENING for manual command button`() {
        assertEquals(VoiceState.DISABLED, sm.state)
        assertTrue(sm.dispatch(VoiceEvent.CommandRequested(CommandTrigger.MANUAL_BUTTON)))
        assertEquals(VoiceState.COMMAND_LISTENING, sm.state)
    }

    @Test
    fun `WAKE_LISTENING can transition directly to COMMAND_LISTENING for manual command button`() {
        sm.dispatch(VoiceEvent.EnableWake)
        assertEquals(VoiceState.WAKE_LISTENING, sm.state)
        assertTrue(sm.dispatch(VoiceEvent.CommandRequested(CommandTrigger.MANUAL_BUTTON)))
        assertEquals(VoiceState.COMMAND_LISTENING, sm.state)
    }

    @Test
    fun `Error event enters RECOVERING and RecoveryComplete returns to WAKE_LISTENING`() {
        sm.dispatch(VoiceEvent.EnableWake)
        assertTrue(sm.dispatch(VoiceEvent.Error))
        assertEquals(VoiceState.RECOVERING, sm.state)
        assertTrue(sm.dispatch(VoiceEvent.RecoveryComplete))
        assertEquals(VoiceState.WAKE_LISTENING, sm.state)
    }

    @Test
    fun `isWakeListening and isCommandListening helpers`() {
        sm.dispatch(VoiceEvent.EnableWake)
        assertTrue(sm.isWakeListening)
        assertFalse(sm.isCommandListening)

        sm.dispatch(VoiceEvent.CommandRequested(CommandTrigger.MANUAL_BUTTON))
        assertTrue(sm.isCommandListening)
        assertFalse(sm.isWakeListening)
    }
}


