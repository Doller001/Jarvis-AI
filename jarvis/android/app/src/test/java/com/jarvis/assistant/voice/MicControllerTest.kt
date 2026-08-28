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
        assertTrue(controller.isAvailable())
        assertNull(controller.getCurrentOwner())
        assertTrue(controller.acquireMic(MicController.OWNER_STT))
        assertFalse(controller.isAvailable())
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
        assertFalse(controller.acquireMic(MicController.OWNER_STT))
        assertEquals(MicController.OWNER_WAKE, controller.getCurrentOwner())

        controller.releaseMic(MicController.OWNER_WAKE)
        assertTrue(controller.isAvailable())
        assertTrue(controller.acquireMic(MicController.OWNER_STT))
        assertEquals(MicController.OWNER_STT, controller.getCurrentOwner())
    }

    @Test
    fun `releasing mic allows new applicant to acquire`() {
        val controller = MicController(context = null)
        assertTrue(controller.acquireMic(MicController.OWNER_WAKE))
        controller.releaseMic(MicController.OWNER_WAKE)
        assertTrue(controller.isAvailable())
        assertTrue(controller.acquireMic(MicController.OWNER_STT))
        assertEquals(MicController.OWNER_STT, controller.getCurrentOwner())
    }

    @Test
    fun `releasing from non-owner does not steal mic`() {
        val controller = MicController(context = null)
        assertTrue(controller.acquireMic(MicController.OWNER_WAKE))
        controller.releaseMic(MicController.OWNER_STT)
        assertFalse(controller.isAvailable())
        assertEquals(MicController.OWNER_WAKE, controller.getCurrentOwner())
    }

    @Test
    fun `releaseAny unconditionally frees the mic`() {
        val controller = MicController(context = null)
        assertTrue(controller.acquireMic(MicController.OWNER_WAKE))
        controller.releaseAny()
        assertTrue(controller.isAvailable())
        assertNull(controller.getCurrentOwner())
    }

    @Test
    fun `forceAcquire overrides current owner`() {
        val controller = MicController(context = null)
        assertTrue(controller.acquireMic(MicController.OWNER_WAKE))
        assertTrue(controller.forceAcquire(MicController.OWNER_STT))
        assertEquals(MicController.OWNER_STT, controller.getCurrentOwner())
    }

    @Test
    fun `same owner can re-acquire without contention`() {
        val controller = MicController(context = null)
        assertTrue(controller.acquireMic(MicController.OWNER_WAKE))
        assertTrue(controller.acquireMic(MicController.OWNER_WAKE))
        assertEquals(MicController.OWNER_WAKE, controller.getCurrentOwner())
    }

    @Test
    fun `transferOwnership succeeds when from matches current`() {
        val controller = MicController(context = null)
        assertTrue(controller.acquireMic(MicController.OWNER_WAKE))
        assertTrue(controller.transferOwnership(MicController.OWNER_WAKE, MicController.OWNER_STT))
        assertEquals(MicController.OWNER_STT, controller.getCurrentOwner())
    }

    @Test
    fun `transferOwnership fails when from does not match`() {
        val controller = MicController(context = null)
        assertTrue(controller.acquireMic(MicController.OWNER_WAKE))
        assertFalse(controller.transferOwnership(MicController.OWNER_STT, MicController.OWNER_INTERRUPT))
        assertEquals(MicController.OWNER_WAKE, controller.getCurrentOwner())
    }

    @Test
    fun `isOwnedBy checks specific owner`() {
        val controller = MicController(context = null)
        assertFalse(controller.isOwnedBy(MicController.OWNER_WAKE))
        controller.acquireMic(MicController.OWNER_WAKE)
        assertTrue(controller.isOwnedBy(MicController.OWNER_WAKE))
        assertFalse(controller.isOwnedBy(MicController.OWNER_STT))
    }
}
