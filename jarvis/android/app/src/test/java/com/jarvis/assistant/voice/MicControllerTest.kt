package com.jarvis.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MicControllerTest {

    @Test
    fun `exclusive ownership is granted to first applicant`() {
        val controller = MicController(context = null)
        assertTrue(controller.isMicAvailable())
        assertNull(controller.getCurrentOwner())

        assertTrue(controller.acquireMic("SpeechController"))
        assertFalse(controller.isMicAvailable())
        assertEquals("SpeechController", controller.getCurrentOwner())
    }

    @Test
    fun `second applicant is denied while first holds mic`() {
        val controller = MicController(context = null)
        assertTrue(controller.acquireMic("AudioCapture"))
        assertFalse(controller.acquireMic("SpeechController"))
        assertEquals("AudioCapture", controller.getCurrentOwner())
    }

    @Test
    fun `releasing mic allows new applicant to acquire`() {
        val controller = MicController(context = null)
        assertTrue(controller.acquireMic("AudioCapture"))
        controller.releaseMic("AudioCapture")
        assertTrue(controller.isMicAvailable())

        assertTrue(controller.acquireMic("SpeechController"))
        assertEquals("SpeechController", controller.getCurrentOwner())
    }

    @Test
    fun `releasing from non-owner does not steal mic`() {
        val controller = MicController(context = null)
        assertTrue(controller.acquireMic("AudioCapture"))
        controller.releaseMic("RandomHacker")
        assertFalse(controller.isMicAvailable())
        assertEquals("AudioCapture", controller.getCurrentOwner())
    }
}
