package com.jarvis.assistant.network

import org.junit.Assert.*
import org.junit.Test

class BackendHealthManagerTest {

    @Test
    fun `default backend health is online and connected`() {
        val health = BackendHealth()
        assertEquals(HealthStatus.CONNECTED, health.status)
        assertTrue(health.httpHealthy)
        assertTrue(health.isNetworkAvailable)
        assertFalse(health.isOfflineMode)
    }

    @Test
    fun `manager defaults to online and only transitions to offline when setOfflineMode is called`() {
        val manager = BackendHealthManager(context = null)
        assertEquals(HealthStatus.CONNECTED, manager.health.value.status)
        assertFalse(manager.health.value.isOfflineMode)

        manager.setOfflineMode(true)
        assertEquals(HealthStatus.OFFLINE, manager.health.value.status)
        assertTrue(manager.health.value.isOfflineMode)

        manager.setOfflineMode(false)
        assertEquals(HealthStatus.CONNECTED, manager.health.value.status)
        assertFalse(manager.health.value.isOfflineMode)

        manager.release()
    }

    @Test
    fun `start with isOfflineMode true initializes in offline state`() {
        val manager = BackendHealthManager(context = null)
        manager.start(isOfflineMode = true)

        assertEquals(HealthStatus.OFFLINE, manager.health.value.status)
        assertTrue(manager.health.value.isOfflineMode)

        manager.release()
    }
}
