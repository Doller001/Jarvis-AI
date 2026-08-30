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
        assertEquals("https://jarvis-ai-59qd.onrender.com", health.endpoint)
    }

    @Test
    fun `updateEndpoint updates endpoint and maintains online health tracking`() {
        val manager = BackendHealthManager(context = null)
        assertEquals("https://jarvis-ai-59qd.onrender.com", manager.health.value.endpoint)

        val updated = manager.updateEndpoint("https://custom-backend.com")
        assertTrue(updated)
        assertEquals("https://custom-backend.com", manager.health.value.endpoint)

        manager.release()
    }

    @Test
    fun `manager starts in online connected state`() {
        val manager = BackendHealthManager(context = null)
        assertEquals(HealthStatus.CONNECTED, manager.health.value.status)
        assertTrue(manager.health.value.isNetworkAvailable)

        manager.release()
    }
}
