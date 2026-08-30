# Android Client Multimodal Orchestrator Integration Specification

- **Date**: 2026-08-30
- **Status**: Approved
- **Author**: Antigravity & Minaty
- **Topic**: Android Client Telemetry Ingestion, Vision Frame Encoding & Multimodal Backend Dispatch

---

## 1. Overview & Objectives

Integrate the Android client with the backend **Autonomous Agent Orchestrator** by providing:
1. **Device Telemetry Collector**: Captures battery level, charging status, network type (Wi-Fi, cellular, offline), audio route, and current volume.
2. **Vision Capture Manager**: Utility to encode bitmap/camera image frames into downscaled Base64 JPEG strings for low-latency visual queries.
3. **Multimodal API & WebSocket Dispatch**: Upgrades `ApiClient` and `WebSocketClient` to attach live sensory telemetry and visual payloads with backwards-compatible fallbacks.

---

## 2. Architecture & Data Flow

```
  [BatteryManager / ConnectivityManager / AudioManager]
                        │
                        ▼
            [DeviceTelemetryCollector]
                        │
                        ▼
     [SensoryTelemetryPayload & MultimodalPayload]
                        │
                        ▼
       [ApiClient.sendMultimodalChat / WebSocket]
                        │
                        ▼
   [Backend Autonomous Agent Orchestrator (/api/v1/chat)]
```

---

## 3. Subsystem Specifications

### 3.1 Telemetry & Network Data Models (`network/SensoryPayload.kt`)
```kotlin
data class SensoryTelemetryPayload(
    val batteryLevel: Int? = null,
    val isCharging: Boolean? = null,
    val networkType: String? = null, // "wifi", "cellular", "offline"
    val volumeLevel: Int? = null,
    val currentAudioOutput: String? = null,
    val extraSensors: Map<String, Any> = emptyMap()
) {
    fun toJsonObject(): JSONObject { ... }
}

data class MultimodalPayload(
    val text: String,
    val sessionId: String = "default-session",
    val requestId: String = "req-${UUID.randomUUID()}",
    val sensoryData: SensoryTelemetryPayload? = null,
    val imageBase64: String? = null,
    val imageUri: String? = null
) {
    fun toJsonObject(): JSONObject { ... }
}
```

### 3.2 Device Telemetry Collector (`telemetry/DeviceTelemetryCollector.kt`)
- `getLiveTelemetry(): SensoryTelemetryPayload`
- Query battery percentage & charging state via `BatteryManager`.
- Query active network transport (`wifi`, `cellular`, `offline`) via `ConnectivityManager`.
- Query volume percentage and audio device route via `AudioManager`.

### 3.3 Vision Frame Capture (`vision/VisionCaptureManager.kt`)
- `encodeBitmapToBase64(bitmap: Bitmap, maxDimension: Int = 1024, quality: Int = 85): String`
- Scales image proportionally to stay within bandwidth budgets and compresses to Base64 JPEG.

### 3.4 ApiClient & WebSocket Updates
- `ApiClient.sendMultimodalChat(payload: MultimodalPayload, onResult: (ChatResult) -> Unit)`
- `ApiClient.sendChat(...)` updated to automatically attach telemetry if available.

---

## 4. Testing Strategy
- Unit tests in `jarvis/android/app/src/test/kotlin/com/jarvis/assistant/telemetry/`:
  - `DeviceTelemetryCollectorTest.kt`
  - `SensoryPayloadTest.kt`
  - `VisionCaptureManagerTest.kt`
