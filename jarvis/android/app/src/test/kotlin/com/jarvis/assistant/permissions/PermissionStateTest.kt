package com.jarvis.assistant.permissions

import org.junit.Assert.*
import org.junit.Test

/**
 * Verifies the real permission-aggregation used by the onboarding flow.
 * Pure JVM logic (no Android context required).
 */
class PermissionStateTest {

    @Test
    fun `default state grants nothing and is not ready`() {
        val s = PermissionState()
        assertEquals(0, s.grantedCount)
        assertFalse(s.allRequiredGranted)
    }

    @Test
    fun `grantedCount reflects only true flags`() {
        val s = PermissionState(
            isMicrophoneGranted = true,
            isNotificationGranted = true,
            isCameraGranted = true
        )
        // 3 true (mic, notif, camera); 5 remain false
        assertEquals(3, s.grantedCount)
    }

    @Test
    fun `allRequiredGranted true only when every required permission is set`() {
        val base = PermissionState(
            isMicrophoneGranted = true,
            isNotificationGranted = true,
            isAccessibilityGranted = true,
            isBatteryOptimizationIgnored = true,
            isCallPhoneGranted = true,
            isContactsGranted = true,
            isSmsGranted = true
        )
        assertTrue(base.allRequiredGranted)

        // Dropping any single required permission breaks readiness.
        assertFalse(base.copy(isMicrophoneGranted = false).allRequiredGranted)
        assertFalse(base.copy(isAccessibilityGranted = false).allRequiredGranted)
        assertFalse(base.copy(isSmsGranted = false).allRequiredGranted)

        // Optional permission (camera) does NOT affect readiness.
        assertTrue(base.copy(isCameraGranted = false).allRequiredGranted)
    }

    @Test
    fun `manager with null context reports all-denied state`() {
        val mgr = PermissionManager(null)
        val s = mgr.checkPermissionState()
        assertFalse(s.allRequiredGranted)
        assertEquals(0, s.grantedCount)
    }
}
