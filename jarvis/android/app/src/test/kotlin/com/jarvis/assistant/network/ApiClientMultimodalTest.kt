package com.jarvis.assistant.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ApiClientMultimodalTest {

    @Test
    fun testMultimodalPayloadConstructors() {
        val telemetry = SensoryTelemetryPayload(
            batteryLevel = 95,
            isCharging = true,
            networkType = "wifi"
        )
        val payload = MultimodalPayload(
            text = "Describe image and device state",
            sessionId = "test-session-1",
            sensoryData = telemetry,
            imageBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        )

        assertEquals("Describe image and device state", payload.text)
        assertEquals("test-session-1", payload.sessionId)
        assertNotNull(payload.sensoryData)
        assertEquals(95, payload.sensoryData?.batteryLevel)
        assertEquals("wifi", payload.sensoryData?.networkType)
        assertNotNull(payload.imageBase64)
    }
}
