# Android Client Multimodal Orchestrator Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement full multimodal integration on Android client by collecting device telemetry (battery, network, audio route, volume), encoding camera/screen vision frames, and dispatching multimodal payloads to the backend Autonomous Agent Orchestrator.

**Architecture:** 
- `SensoryPayload.kt`: Typed Kotlin models for `SensoryTelemetryPayload` and `MultimodalPayload` with JSON serialization.
- `DeviceTelemetryCollector.kt`: Hardware/OS sensor reader using Android system services (`BatteryManager`, `ConnectivityManager`, `AudioManager`).
- `VisionCaptureManager.kt`: Helper for bitmap scaling and Base64 JPEG encoding.
- `ApiClient.kt` & `WebSocketClient.kt`: Extended with `sendMultimodalChat` and automated telemetry enrichment.

**Tech Stack:** Kotlin 1.9, Android SDK 34, OkHttp 4.12, JUnit 4, Mockito.

**Spec:** [docs/superpowers/specs/2026-08-30-android-multimodal-orchestrator-integration.md](file:///home/shanu/Desktop/and9/docs/superpowers/specs/2026-08-30-android-multimodal-orchestrator-integration.md)

## Global Constraints

- Zero breaking changes to existing `ApiClient.sendChat` and WebSocket protocol contracts.
- Graceful handling of null `Context` or missing permissions (return default safe telemetry values without crashing).
- All unit tests must pass.

---

### Task 1: Sensory & Multimodal Payload Data Models

**Files:**
- Create: `jarvis/android/app/src/main/kotlin/com/jarvis/assistant/network/SensoryPayload.kt`
- Test: `jarvis/android/app/src/test/kotlin/com/jarvis/assistant/network/SensoryPayloadTest.kt`

**Interfaces:**
- Consumes: Standard `org.json.JSONObject`, `java.util.UUID`
- Produces: `SensoryTelemetryPayload` data class, `MultimodalPayload` data class

- [ ] **Step 1: Write the failing test**

```kotlin
// jarvis/android/app/src/test/kotlin/com/jarvis/assistant/network/SensoryPayloadTest.kt
package com.jarvis.assistant.network

import org.junit.Assert.*
import org.junit.Test

class SensoryPayloadTest {
    @Test
    fun testSensoryTelemetryJsonSerialization() {
        val telemetry = SensoryTelemetryPayload(
            batteryLevel = 85,
            isCharging = true,
            networkType = "wifi",
            volumeLevel = 60,
            currentAudioOutput = "speaker"
        )
        val json = telemetry.toJsonObject()
        assertEquals(85, json.getInt("battery_level"))
        assertTrue(json.getBoolean("is_charging"))
        assertEquals("wifi", json.getString("network_type"))
        assertEquals(60, json.getInt("volume_level"))
        assertEquals("speaker", json.getString("current_audio_output"))
    }

    @Test
    fun testMultimodalPayloadJsonSerialization() {
        val telemetry = SensoryTelemetryPayload(batteryLevel = 90, isCharging = false, networkType = "cellular")
        val payload = MultimodalPayload(
            text = "Analyze battery status",
            sessionId = "session-123",
            sensoryData = telemetry,
            imageBase64 = "base64_image_data_here"
        )
        val json = payload.toJsonObject()
        assertEquals("Analyze battery status", json.getString("text"))
        assertEquals("session-123", json.getString("session_id"))
        assertEquals("base64_image_data_here", json.getString("image_base64"))
        assertTrue(json.has("sensory_data"))
        assertEquals(90, json.getJSONObject("sensory_data").getInt("battery_level"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**
- [ ] **Step 3: Write minimal implementation in `SensoryPayload.kt`**
- [ ] **Step 4: Run test to verify it passes**
- [ ] **Step 5: Commit**

---

### Task 2: Device Telemetry Collector

**Files:**
- Create: `jarvis/android/app/src/main/kotlin/com/jarvis/assistant/telemetry/DeviceTelemetryCollector.kt`
- Test: `jarvis/android/app/src/test/kotlin/com/jarvis/assistant/telemetry/DeviceTelemetryCollectorTest.kt`

**Interfaces:**
- Consumes: `android.content.Context`, `SensoryTelemetryPayload`
- Produces: `DeviceTelemetryCollector` class with `getLiveTelemetry(): SensoryTelemetryPayload`

- [ ] **Step 1: Write the failing test**
- [ ] **Step 2: Run test to verify it fails**
- [ ] **Step 3: Write minimal implementation in `DeviceTelemetryCollector.kt`**
- [ ] **Step 4: Run test to verify it passes**
- [ ] **Step 5: Commit**

---

### Task 3: Vision Frame Encoder Utility

**Files:**
- Create: `jarvis/android/app/src/main/kotlin/com/jarvis/assistant/vision/VisionCaptureManager.kt`
- Test: `jarvis/android/app/src/test/kotlin/com/jarvis/assistant/vision/VisionCaptureManagerTest.kt`

**Interfaces:**
- Consumes: `android.graphics.Bitmap`, `java.io.ByteArrayOutputStream`, `android.util.Base64`
- Produces: `VisionCaptureManager` object with `encodeBitmapToBase64(bitmap: Bitmap, maxDimension: Int = 1024, quality: Int = 85): String`

- [ ] **Step 1: Write the failing test**
- [ ] **Step 2: Run test to verify it fails**
- [ ] **Step 3: Write minimal implementation in `VisionCaptureManager.kt`**
- [ ] **Step 4: Run test to verify it passes**
- [ ] **Step 5: Commit**

---

### Task 4: ApiClient & WebSocket Multimodal Dispatch Integration

**Files:**
- Modify: `jarvis/android/app/src/main/kotlin/com/jarvis/assistant/network/ApiClient.kt`
- Modify: `jarvis/android/app/src/main/kotlin/com/jarvis/assistant/network/WebSocketClient.kt`
- Test: `jarvis/android/app/src/test/kotlin/com/jarvis/assistant/network/ApiClientMultimodalTest.kt`

**Interfaces:**
- Consumes: `MultimodalPayload`, `SensoryTelemetryPayload`, `DeviceTelemetryCollector`
- Produces: `ApiClient.sendMultimodalChat(...)`, `ApiClient.sendChat(...)` attaching telemetry

- [ ] **Step 1: Write the failing test**
- [ ] **Step 2: Run test to verify it fails**
- [ ] **Step 3: Write minimal implementation**
- [ ] **Step 4: Run test to verify it passes**
- [ ] **Step 5: Commit**
