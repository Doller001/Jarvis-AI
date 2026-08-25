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
        assertTrue(controller.acquireMic(MicController.OWNER_STT))
        assertFalse(controller.isMicAvailable())
        assertEquals(MicController.OWNER_STT, controller.getCurrentOwner())
    }

    @Test
    fun `second applicant is denied while first holds mic`() {
        val controller = MicController(context = null)
        assertTrue(controller.acquireMic(MicController.OWNER_WAKE))
        assertFalse(controller.acquireMic(MicController.OWNER_STT))
        assertEquals(MicController.OWNER_WAKE, controller.getCurrentOwner())
    }

    @Test
    fun `WAKE and STT cannot hold mic simultaneously`() {
        val controller = MicController(context = null)
        assertTrue(controller.acquireMic(MicController.OWNER_WAKE))
        // STT must be denied while WAKE holds the mic.
        assertFalse(controller.acquireMic(MicController.OWNER_STT))
        assertEquals(MicController.OWNER_WAKE, controller.getCurrentOwner())

        // After WAKE releases, STT can acquire.
        controller.releaseMic(MicController.OWNER_WAKE)
        assertTrue(controller.isMicAvailable())
        assertTrue(controller.acquireMic(MicController.OWNER_STT))
        assertEquals(MicController.OWNER_STT, controller.getCurrentOwner())
    }

    @Test
    fun `releasing mic allows new applicant to acquire`() {
        val controller = MicController(context = null)
        assertTrue(controller.acquireMic(MicController.OWNER_WAKE))
        controller.releaseMic(MicController.OWNER_WAKE)
        assertTrue(controller.isMicAvailable())
        assertTrue(controller.acquireMic(MicController.OWNER_STT))
        assertEquals(MicController.OWNER_STT, controller.getCurrentOwner())
    }

    @Test
    fun `releasing from non-owner does not steal mic`() {
        val controller = MicController(context = null)
        assertTrue(controller.acquireMic(MicController.OWNER_WAKE))
        controller.releaseMic(MicController.OWNER_STT)  // wrong owner
        assertFalse(controller.isMicAvailable())
        assertEquals(MicController.OWNER_WAKE, controller.getCurrentOwner())
    }

    @Test
    fun `releaseAny unconditionally frees the mic`() {
        val controller = MicController(context = null)
        assertTrue(controller.acquireMic(MicController.OWNER_WAKE))
        controller.releaseAny()
        assertTrue(controller.isMicAvailable())
        assertNull(controller.getCurrentOwner())
    }

    @Test
    fun `forceAcquire overrides current owner`() {
        val controller = MicController(context = null)
        assertTrue(controller.acquireMic(MicController.OWNER_WAKE))
        // forceAcquire should succeed even when WAKE holds the mic.
        assertTrue(controller.forceAcquire(MicController.OWNER_STT))
        assertEquals(MicController.OWNER_STT, controller.getCurrentOwner())
    }

    @Test
    fun `same owner can re-acquire without contention`() {
        val controller = MicController(context = null)
        assertTrue(controller.acquireMic(MicController.OWNER_WAKE))
        // Re-acquiring as the same owner is idempotent.
        assertTrue(controller.acquireMic(MicController.OWNER_WAKE))
        assertEquals(MicController.OWNER_WAKE, controller.getCurrentOwner())
    }

    @Test
    fun `isOwnershipValid always true (exclusivity enforced by acquireMic)`() {
        val controller = MicController(context = null)
        assertTrue(controller.isOwnershipValid())
        controller.acquireMic(MicController.OWNER_WAKE)
        assertTrue(controller.isOwnershipValid())
    }
}
