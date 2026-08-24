# 📋 JARVIS / AND9 — Baseline Feature & Implementation Matrix (Phase 0)

> **Baseline Freeze Date:** 2026-08-24  
> **Target OS:** Android 12+ (API 31-34) / Linux Backend (Python 3.12 FastAPI)  
> **Status Taxonomy:**  
> - 🟢 **REAL / PASS:** Fully implemented with real platform APIs and operational logic.  
> - 🟡 **PARTIAL:** Implemented with limitations, hardcoded assumptions, or missing fallback cascades.  
> - 🔴 **FAKE / PLACEHOLDER / STUB:** Mocked return values, simulated states, or deceptive success flags.  
> - ⚪ **NOT IMPLEMENTED / PLANNED:** Explicitly declared in roadmap or architecture but missing in codebase.

---

## 1. 🎙️ Acoustic, DSP & Voice Engine

| Feature / Subsystem | Current Code Location | Implementation Reality | Baseline Status | Notes / Limitations |
| :--- | :--- | :--- | :---: | :--- |
| **Butterworth HPF (85Hz/135Hz)** | `voice/NearFieldAudioProcessor.kt` | Real 2nd order IIR filter attenuating sub-bass frequencies. | 🟢 **PASS** | Runs in 10ms frame loop without heap allocations. |
| **Noise Floor Tracker** | `voice/NearFieldAudioProcessor.kt` | Real rolling RMS calculation (-75dBFS to -25dBFS). | 🟢 **PASS** | Dynamic background noise adaptation. |
| **Nearest-Voice Gating** | `voice/NearFieldAudioProcessor.kt` | Real speech formant (300-3400Hz) ratio vs ambient energy gating. | 🟢 **PASS** | Threshold set to +12dB SNR. |
| **Dual-Metric VAD (Energy + ZCR)** | `voice/VadEngine.kt` | Real zero-crossing rate and peak-to-average energy calculation. | 🟢 **PASS** | Operates on 160-sample PCM chunks. |
| **AGC Soft-Peak Limiter** | `voice/NearFieldAudioProcessor.kt` | Real soft-knee compression targeting -16dBFS. | 🟢 **PASS** | Prevents microphone clipping distortion. |
| **Low-Latency Audio Capture** | `voice/LowLatencyAudioCapture.kt` | Real `AudioRecord` thread running with `THREAD_PRIORITY_URGENT_AUDIO`. | 🟢 **PASS** | Reads 160-sample (10ms) frames continuously. |
| **Audio Route & Bluetooth SCO** | `voice/AudioRouteManager.kt` | Real `AudioManager` SCO connect/disconnect broadcasts. | 🟢 **PASS** | Handles headset plug/unplug and Bluetooth SCO. |
| **Single Mic Owner Enforcement** | `voice/MicController.kt` | Real thread-safe atomic lock string mechanism. | 🟢 **PASS** | Enforces single component mic access. |

---

## 2. ⚡ Wake Word Subsystem

| Feature / Subsystem | Current Code Location | Implementation Reality | Baseline Status | Notes / Limitations |
| :--- | :--- | :--- | :---: | :--- |
| **Offline ONNX KWS Inference** | `voice/wakeword/OnnxWakeWordDetector.kt` | Real ONNX Runtime inference using `melspectrogram.onnx` + `hey_jarvis.onnx`. | 🟢 **PASS** | Evaluates rolling PCM buffer against threshold. |
| **Wake Word State Machine Handoff** | `voice/VoiceRuntime.kt` | Coordinates `WAKE` -> `LISTENING` transition and mic handoff. | 🟢 **PASS** | Plays feedback tone and pauses wake detector. |
| **Wake-Word Continuous STT Fallback** | `voice/wakeword/LiveKitWakeWordEngine.kt:178-240` | Loops Android `SpeechRecognizer` continuously looking for "hey jarvis". | 🔴 **PROHIBITED FALLBACK** | **Severe battery & privacy drain.** Continuous `SpeechRecognizer` must NOT be used as a wake word fallback. |
| **Lightweight Local KWS Fallback** | — | Not present. Only ONNX or continuous STT loop exists. | ⚪ **NOT IMPLEMENTED** | Required as lightweight offline fallback before PTT. |
| **Push-to-Talk Fallback** | `ui/MainActivity.kt`, `ui/JarvisViewModel.kt` | Real manual mic button in Compose UI triggers command mode directly. | 🟢 **PASS** | Clean fallback when wake word is disabled. |

---

## 3. 🗣️ Speech-to-Text (STT) & Text-to-Speech (TTS)

| Feature / Subsystem | Current Code Location | Implementation Reality | Baseline Status | Notes / Limitations |
| :--- | :--- | :--- | :---: | :--- |
| **Android System SpeechRecognizer** | `voice/SpeechController.kt` | Real Google / Android SpeechRecognizer with partial results. | 🟢 **PASS** | Works reliably with network connection. |
| **Sherpa-ONNX Local Streaming STT** | — | Missing. Relies 100% on standard Android SpeechRecognizer. | ⚪ **NOT IMPLEMENTED** | Primary local streaming engine not yet integrated. |
| **Multi-Engine STT Cascade** | — | No `STTEngine` abstraction with Sherpa -> Local -> Cloud. | ⚪ **NOT IMPLEMENTED** | Hardcoded to Android SpeechRecognizer. |
| **Android System TextToSpeech** | `voice/TextToSpeechEngine.kt` | Real `android.speech.tts.TextToSpeech` with queueing and callbacks. | 🟢 **PASS** | Locale cascade (en-IN -> default -> en-US). |
| **Sherpa / Piper Neural Offline TTS** | — | Missing. Relies 100% on Android system TTS. | ⚪ **NOT IMPLEMENTED** | Local high-quality neural voice missing. |
| **TTS Controls (Pause / Resume / Pitch)** | `voice/TextToSpeechEngine.kt` | Only `speak()`, `stop()`, `setSpeechRate()`, `shutdown()`. | 🟡 **PARTIAL** | Missing `pause()`, `resume()`, `setPitch()`, `setVoice()`. |
| **Lazy Engine Lifecycle** | `voice/VoiceRuntime.kt` | Eagerly instantiates `SpeechController`, `TextToSpeechEngine`, `AudioCapture` on init. | 🔴 **VIOLATION** | Keeps heavy resources loaded even during IDLE resting state. |

---

## 4. 🧠 Memory Engine (CAG, RAG, MAG)

| Feature / Subsystem | Current Code Location | Implementation Reality | Baseline Status | Notes / Limitations |
| :--- | :--- | :--- | :---: | :--- |
| **SQLite3 Persistent Database** | `memory/JarvisMemoryDatabase.kt` | Real SQLite database with WAL mode and tables (`cag_cache`, `rag_chunks`, etc.). | 🟢 **PASS** | Stores episodes, facts, user key-values, and CAG cache. |
| **CAG Exact Answer Match (< 0.1ms)** | `memory/MemoryEngine.kt` | Real SHA-256 hash lookup in ConcurrentHashMap and SQLite table. | 🟢 **PASS** | Sub-millisecond instant recall. |
| **CAG Near Match (< 4ms)** | `memory/MemoryEngine.kt` | Real Jaccard token similarity + Levenshtein distance scoring. | 🟢 **PASS** | Matches similar queries above 0.88 threshold. |
| **RAG Knowledge Chunk Retrieval** | `memory/MemoryEngine.kt` | Real token-overlap search over `rag_chunks` table. | 🟡 **PARTIAL** | Pure keyword/token overlap; lacks vector embeddings & reranker. |
| **MAG Episodic History Logging** | `memory/MemoryEngine.kt` | Real turn-by-turn logging in `mem_episodes`. | 🟢 **PASS** | Queryable by timestamp and turn count. |
| **MAG Automatic Fact Extraction** | `memory/MemoryEngine.kt:289-305` | Only 3 hardcoded regex patterns (`my name is`, `i live in`, `remember that`). | 🔴 **STUB / PRIMITIVE** | No LLM-driven or semantic entity extraction. |
| **Backend SQLite Memory Manager** | `jarvis/backend/app/memory/persistent_store.py` | Real SQLite DB storing conversation turns and structured memories. | 🟢 **PASS** | Session-isolated memory storage. |

---

## 5. 🤖 Task Engine, Adapters & Automation

| Feature / Subsystem | Current Code Location | Implementation Reality | Baseline Status | Notes / Limitations |
| :--- | :--- | :--- | :---: | :--- |
| **Local Rule-Based Task Planner** | `actionengine/planner/LocalTaskPlanner.kt` | Real regex/keyword rule matching decomposing queries into steps. | 🟡 **PARTIAL** | Hardcoded heuristics; cannot handle arbitrary multi-branch DAGs. |
| **Multi-Step Action Execution Pipeline** | `actionengine/core/ActionExecutor.kt` | Executes list of `ActionStep` sequentially with retry logic. | 🟡 **PARTIAL** | Executes steps sequentially but lacks proper verification. |
| **Action Verification Layer** | `actionengine/core/ActionExecutor.kt:89` | Hardcoded `verificationPassed = executionSuccess`. | 🔴 **FAKE / SIMULATED** | Does NOT inspect hardware or app state after execution. |
| **Wait-Until State Condition** | `actionengine/core/ActionExecutor.kt:124-128` | Uses blind `delay(durationMs)` (default 1000ms). | 🔴 **PLACEHOLDER** | Blind sleep instead of polling accessibility node or package state. |
| **Contact Resolver** | `actionengine/core/ActionExecutor.kt:174-177` | Echoes back `{contact: contact}` without querying `ContactsContract`. | 🔴 **STUB** | Real contact lookup bypassed in `ActionExecutor`. |
| **YouTube Adapter** | `actionengine/adapter/YouTubeAdapter.kt` | Real deep link intent with search URL targeting YouTube package. | 🟢 **PASS** | Launches YouTube directly to search query. |
| **WhatsApp Adapter** | `actionengine/adapter/WhatsAppAdapter.kt` | Real `Intent.ACTION_VIEW` targeting `com.whatsapp` with phone/text URI. | 🟢 **PASS** | Formats international phone number and payload. |
| **Chrome Adapter** | `actionengine/adapter/ChromeAdapter.kt` | Real URL launch intent targeting `com.android.chrome`. | 🟢 **PASS** | Resolves web queries or direct URLs. |
| **Phone Adapter (Call & Log)** | `actionengine/adapter/PhoneAdapter.kt` | Real `ACTION_CALL`/`ACTION_DIAL` and `ContactsContract` / `CallLog` reader. | 🟢 **PASS** | Gated by dangerous permission checks. |

---

## 6. 📱 Device Capabilities & System Controllers

| Capability / Controller | Current Code Location | Implementation Reality | Baseline Status | Current Execution Mode |
| :--- | :--- | :--- | :---: | :--- |
| **Torch / Flashlight** | `device/SystemController.kt` | Real `CameraManager.setTorchMode(true/false)`. | 🟢 **PASS** | `DIRECT` (CameraManager) |
| **Volume Control** | `device/SystemController.kt` | Real `AudioManager.setStreamVolume()`. | 🟢 **PASS** | `DIRECT` (AudioManager) |
| **Wi-Fi Settings** | `device/SystemController.kt` | Real `Settings.Panel.ACTION_WIFI` panel intent. | 🟢 **PASS** | `SETTINGS_INTENT` |
| **Bluetooth Settings** | `device/SystemController.kt` | Real `Settings.ACTION_BLUETOOTH_SETTINGS` intent. | 🟢 **PASS** | `SETTINGS_INTENT` |
| **Battery Telemetry** | `device/SystemController.kt` | Real `ACTION_BATTERY_CHANGED` sticky broadcast receiver. | 🟢 **PASS** | `DIRECT` (Broadcast) |
| **Storage Metrics** | `device/SystemController.kt` | Real `StatFs(Environment.getDataDirectory().path)`. | 🟢 **PASS** | `DIRECT` (StatFs) |
| **App Launching** | `device/AppController.kt` | Real `PackageManager.getLaunchIntentForPackage()`. | 🟢 **PASS** | `DIRECT` (PackageManager) |
| **App Closing / Home** | `device/AppController.kt` | Dispatches `GLOBAL_ACTION_HOME` via Accessibility Service. | 🟢 **PASS** | `ACCESSIBILITY` |
| **Media Playback KeyEvents** | `device/MediaController.kt` | Real `KeyEvent(KEYCODE_MEDIA_PLAY_PAUSE)` dispatch. | 🟢 **PASS** | `DIRECT` (AudioManager) |
| **SMS Sending** | `device/SmsController.kt` | Real `SmsManager.sendTextMessage()` with intent fallback. | 🟢 **PASS** | `DIRECT` / `SETTINGS_INTENT` |
| **Screen Reading (OCR / Nodes)** | `accessibility/AccessibilityController.kt` | Real traversal of `AccessibilityNodeInfo` tree with text aggregation. | 🟢 **PASS** | `ACCESSIBILITY` |
| **Programmatic Gestures** | `accessibility/GestureController.kt` | Real `dispatchGesture(GestureDescription)` for tap, swipe, pinch. | 🟢 **PASS** | `ACCESSIBILITY` |
| **Notification Listener** | `services/JarvisNotificationListenerService.kt` | Real `NotificationListenerService` capturing active notifications. | 🟢 **PASS** | `DIRECT` (Service) |
| **Quick Settings Tile** | `services/JarvisQuickTileService.kt` | Real `TileService` toggling foreground service. | 🟢 **PASS** | `DIRECT` (Tile API) |
| **Central Capability Matrix** | — | Scattered across disparate controller classes without capability schema. | ⚪ **NOT IMPLEMENTED** | Lacks unified `DeviceCapabilityManager` exposing execution modes. |

---

## 7. ☁️ Backend Architecture, Tools & Security

| Feature / Subsystem | Current Code Location | Implementation Reality | Baseline Status | Notes / Limitations |
| :--- | :--- | :--- | :---: | :--- |
| **Multi-Provider LLM Gateway** | `jarvis/backend/app/llm/gateway.py` | Real asynchronous routing to Gemini, Groq, Nvidia, OpenRouter, Ollama. | 🟢 **PASS** | Circuit breaker, retries, and fallback cascade active. |
| **WebSocket Realtime Gateway** | `jarvis/backend/app/realtime/ws.py` | Real bidirectional FastAPI WebSocket endpoint with JSON protocol. | 🟢 **PASS** | Heartbeat pings and session isolation. |
| **Backend Task Planner** | `jarvis/backend/app/agent/planner.py` | Returns trivial 1-step plan `{"steps": [{"step_id": 1, ...}]}`. | 🔴 **PLACEHOLDER** | No task DAG decomposition or step dependency tracking. |
| **Tool Calling Structured Output** | `jarvis/backend/app/agent/orchestrator.py` | Relies on system prompt `"output a JSON object"` instead of schema validation. | 🔴 **UNRELIABLE** | Fragile text generation without schema rejection. |
| **Backend Tool: `analyze_image`** | `jarvis/backend/app/tools/executor.py:32-39` | Returns static hardcoded string `"Multimodal analysis completed..."`. | 🔴 **FAKE / MOCK** | Multimodal image analysis is completely fake. |
| **Backend Tool: `get_battery_level`** | `jarvis/backend/app/tools/executor.py:50-56` | Returns static hardcoded string `"Battery level is 85%"`. | 🔴 **FAKE / MOCK** | Telemetry is completely mocked on backend. |
| **Backend Tool: `web_search`** | `jarvis/backend/app/tools/executor.py:143-176` | Real DuckDuckGo API HTTP request extracting instant answer summary. | 🟢 **PASS** | Real live search tool. |
| **Backend Tool: `search_music`** | `jarvis/backend/app/retrieval/music_index.py` | Real ChromaDB / SQLite semantic vector retrieval. | 🟢 **PASS** | Thread-isolated embedding retrieval. |
| **Backend Device Action Protocol** | `jarvis/backend/app/tools/executor.py:86-92` | Backend returns `"status": "success"` before dispatching to device. | 🔴 **VIOLATION** | Optimistically fakes success before device execution & verification. |
| **WebSocket Authentication Gate** | `jarvis/backend/app/security/auth.py:20-25` | Returns `True` if `JARVIS_WS_AUTH_TOKEN` is not set (`missing -> allow`). | 🔴 **SECURITY FLAW** | Defaults to completely open access without authentication. |
| **Risk Confirmation Token Binding** | `jarvis/backend/app/security/token_manager.py` | Real cryptographic HMAC single-use token expiring in 120s. | 🟢 **PASS** | Binds action + parameters + session + request ID. |

---

## 8. 🛡️ Permissions, Lifecycle & Background Reliability

| Subsystem / Feature | Current Code Location | Implementation Reality | Baseline Status | Notes / Limitations |
| :--- | :--- | :--- | :---: | :--- |
| **Foreground Service Lifecycle** | `services/JarvisForegroundService.kt` | Real Android Foreground Service with microphone type. | 🟢 **PASS** | Persistent notification with live status updates. |
| **Static UI Callback Bridges** | `services/JarvisForegroundService.kt:21-34` | Static companion object lambda properties (`onUtterance`, `speak`, etc.). | 🔴 **CRITICAL DEFECT** | Causes memory leaks and broken callbacks on Activity recreation. |
| **Boot Auto-Start Recovery** | `services/BootRecoveryReceiver.kt` | Real `RECEIVE_BOOT_COMPLETED` receiver relaunching service. | 🟢 **PASS** | Restores background service on reboot. |
| **OEM Battery Optimization Request** | `device/OemOptimizer.kt` | Real `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` and manufacturer intents. | 🟢 **PASS** | Guides user through Xiaomi/Samsung background whitelisting. |
| **Package Visibility Permission** | `AndroidManifest.xml:31` | Declares broad `QUERY_ALL_PACKAGES` permission. | 🟡 **AUDIT NEEDED** | Must be evaluated and scoped down to specific `<queries>`. |
| **Dynamic Permission Manager** | `permissions/PermissionManager.kt` | Real status checker for mic, overlay, accessibility, notifications, phone, sms. | 🟢 **PASS** | Checks permission state cleanly across Android versions. |
