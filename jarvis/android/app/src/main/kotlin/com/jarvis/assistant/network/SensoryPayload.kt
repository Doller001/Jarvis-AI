package com.jarvis.assistant.network

import org.json.JSONObject
import java.util.UUID

/**
 * Telemetry data representing device sensors and hardware state.
 */
data class SensoryTelemetryPayload(
    val batteryLevel: Int? = null,
    val isCharging: Boolean? = null,
    val networkType: String? = null,
    val volumeLevel: Int? = null,
    val currentAudioOutput: String? = null,
    val extraSensors: Map<String, Any> = emptyMap()
) {
    /**
     * Serializes telemetry to a [JSONObject] matching backend SensoryTelemetry model.
     */
    fun toJsonObject(): JSONObject {
        val json = JSONObject()
        batteryLevel?.let { json.put("battery_level", it) }
        isCharging?.let { json.put("is_charging", it) }
        networkType?.let { json.put("network_type", it) }
        volumeLevel?.let { json.put("volume_level", it) }
        currentAudioOutput?.let { json.put("current_audio_output", it) }

        val extraJson = JSONObject()
        extraSensors.forEach { (key, value) ->
            extraJson.put(key, value)
        }
        json.put("extra_sensors", extraJson)

        return json
    }
}

/**
 * Unified multimodal payload sent to backend /api/v1/chat endpoint.
 */
data class MultimodalPayload(
    val text: String,
    val sessionId: String = "default-session",
    val requestId: String = "req-${UUID.randomUUID()}",
    val sensoryData: SensoryTelemetryPayload? = null,
    val imageBase64: String? = null,
    val imageUri: String? = null
) {
    /**
     * Serializes payload to a [JSONObject] matching backend MultimodalInputPayload model.
     */
    fun toJsonObject(): JSONObject {
        val json = JSONObject()
        json.put("text", text)
        json.put("session_id", sessionId)
        json.put("request_id", requestId)
        sensoryData?.let { json.put("sensory_data", it.toJsonObject()) }
        imageBase64?.let { json.put("image_base64", it) }
        imageUri?.let { json.put("image_uri", it) }
        return json
    }
}
