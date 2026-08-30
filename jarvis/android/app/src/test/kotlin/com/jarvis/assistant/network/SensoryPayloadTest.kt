package com.jarvis.assistant.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensoryPayloadTest {

    @Test
    fun `SensoryTelemetryPayload defaults to null and empty map`() {
        val payload = SensoryTelemetryPayload()
        assertNull(payload.batteryLevel)
        assertNull(payload.isCharging)
        assertNull(payload.networkType)
        assertNull(payload.volumeLevel)
        assertNull(payload.currentAudioOutput)
        assertTrue(payload.extraSensors.isEmpty())
    }

    @Test
    fun `SensoryTelemetryPayload toJsonObject serializes full fields correctly`() {
        val payload = SensoryTelemetryPayload(
            batteryLevel = 85,
            isCharging = true,
            networkType = "wifi",
            volumeLevel = 10,
            currentAudioOutput = "speaker",
            extraSensors = mapOf("ambient_light" to 420.5, "device_temperature" to 36.2)
        )

        val json = payload.toJsonObject()
        assertEquals(85, json.getInt("battery_level"))
        assertTrue(json.getBoolean("is_charging"))
        assertEquals("wifi", json.getString("network_type"))
        assertEquals(10, json.getInt("volume_level"))
        assertEquals("speaker", json.getString("current_audio_output"))

        assertTrue(json.has("extra_sensors"))
        val extraJson = json.getJSONObject("extra_sensors")
        assertEquals(420.5, extraJson.getDouble("ambient_light"), 0.001)
        assertEquals(36.2, extraJson.getDouble("device_temperature"), 0.001)
    }

    @Test
    fun `SensoryTelemetryPayload toJsonObject handles partial fields and nulls safely`() {
        val payload = SensoryTelemetryPayload(
            batteryLevel = 50,
            networkType = "cellular"
        )

        val json = payload.toJsonObject()
        assertEquals(50, json.getInt("battery_level"))
        assertEquals("cellular", json.getString("network_type"))
        assertFalse(json.has("is_charging"))
        assertFalse(json.has("volume_level"))
        assertFalse(json.has("current_audio_output"))
        assertTrue(json.has("extra_sensors"))
        assertEquals(0, json.getJSONObject("extra_sensors").length())
    }

    @Test
    fun `MultimodalPayload default values are set properly`() {
        val payload = MultimodalPayload(text = "Hello Jarvis")
        assertEquals("Hello Jarvis", payload.text)
        assertEquals("default-session", payload.sessionId)
        assertTrue(payload.requestId.startsWith("req-"))
        assertNull(payload.sensoryData)
        assertNull(payload.imageBase64)
        assertNull(payload.imageUri)
    }

    @Test
    fun `MultimodalPayload toJsonObject serializes text-only payload correctly`() {
        val payload = MultimodalPayload(
            text = "What is the time?",
            sessionId = "session-123",
            requestId = "req-custom-001"
        )

        val json = payload.toJsonObject()
        assertEquals("What is the time?", json.getString("text"))
        assertEquals("session-123", json.getString("session_id"))
        assertEquals("req-custom-001", json.getString("request_id"))
        assertFalse(json.has("sensory_data"))
        assertFalse(json.has("image_base64"))
        assertFalse(json.has("image_uri"))
    }

    @Test
    fun `MultimodalPayload toJsonObject serializes full multimodal payload correctly`() {
        val telemetry = SensoryTelemetryPayload(
            batteryLevel = 90,
            isCharging = false,
            networkType = "wifi"
        )
        val payload = MultimodalPayload(
            text = "Describe what is on screen",
            sessionId = "session-abc",
            requestId = "req-test-999",
            sensoryData = telemetry,
            imageBase64 = "data:image/jpeg;base64,/9j/4AAQSkZJRg==",
            imageUri = "content://media/external/images/media/123"
        )

        val json = payload.toJsonObject()
        assertEquals("Describe what is on screen", json.getString("text"))
        assertEquals("session-abc", json.getString("session_id"))
        assertEquals("req-test-999", json.getString("request_id"))
        assertEquals("data:image/jpeg;base64,/9j/4AAQSkZJRg==", json.getString("image_base64"))
        assertEquals("content://media/external/images/media/123", json.getString("image_uri"))

        assertTrue(json.has("sensory_data"))
        val sensoryJson = json.getJSONObject("sensory_data")
        assertEquals(90, sensoryJson.getInt("battery_level"))
        assertFalse(sensoryJson.getBoolean("is_charging"))
        assertEquals("wifi", sensoryJson.getString("network_type"))
    }

    @Test
    fun `MultimodalPayload copy allows updating individual fields`() {
        val original = MultimodalPayload(text = "Turn on torch")
        val updated = original.copy(
            sessionId = "custom-sess",
            sensoryData = SensoryTelemetryPayload(batteryLevel = 15)
        )

        assertEquals("Turn on torch", updated.text)
        assertEquals("custom-sess", updated.sessionId)
        assertEquals(original.requestId, updated.requestId)
        assertNotNull(updated.sensoryData)
        assertEquals(15, updated.sensoryData?.batteryLevel)
    }
}
