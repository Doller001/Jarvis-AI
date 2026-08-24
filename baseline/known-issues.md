# 🚨 JARVIS / AND9 — Known Issues & Architectural Flaws (Baseline Audit)

> **Document Version:** 1.0.0 (Phase 0 Freeze)  
> **Date:** 2026-08-24  
> **Scope:** Android Client (`jarvis/android`) & Cloud Backend (`jarvis/backend`)

This document catalogs every critical flaw, architectural violation, fake/mock implementation, memory leak, and security risk currently present in the codebase prior to executing Phases 1 through 20.

---

## 1. 🛑 Architectural & Lifecycle Flaws

### 1.1 Monolithic Eager Instantiation on Startup
- **Location:** `com/jarvis/assistant/voice/VoiceRuntime.kt` (lines 22-37) & `JarvisForegroundService.kt`
- **Issue:** `VoiceRuntime` immediately initializes all 7 submodules (`SpeechController`, `TextToSpeechEngine`, `AudioRouteManager`, `NearFieldAudioProcessor`, `LowLatencyAudioCapture`, `LiveKitWakeWordEngine`, `MicController`) during service startup.
- **Impact:** IDLE resting mode allocates over ~52 MB of RAM and holds Android system TTS and audio focus structures in memory continuously.
- **Target Fix (Phase 1):** Implement lazy loading: in IDLE mode, only the Wake Detector, VAD, Audio Capture, and State Machine must be loaded. STT, TTS, LLM, and Planner must be instantiated on-demand.

### 1.2 Static Companion Object Callback Leaks
- **Location:** `com/jarvis/assistant/services/JarvisForegroundService.kt` (lines 21-34)
- **Issue:** The foreground service uses static companion object variables (`var onUtterance`, `var onResponseDone`, `var speak`, etc.) to communicate with `MainActivity` and `JarvisViewModel`.
- **Impact:** If `MainActivity` is destroyed or recreated (screen rotation, memory reclaim), the static lambdas hold references to defunct UI contexts, causing memory leaks and dropped event callbacks.
- **Target Fix (Phase 1):** Replace static lambdas with Kotlin `SharedFlow` / `StateFlow` event buses or a bound Service connection with a clean listener registry.

---

## 2. 🎙️ Voice & Audio Pipeline Defects

### 2.1 Forbidden Continuous SpeechRecognizer Wake-Word Fallback
- **Location:** `com/jarvis/assistant/voice/wakeword/LiveKitWakeWordEngine.kt` (lines 91-101, 178-240)
- **Issue:** When offline ONNX models are absent, `LiveKitWakeWordEngine` starts a continuous looping Android `SpeechRecognizer` session to detect the phrase "hey jarvis".
- **Impact:** Severe battery consumption (~15-25% per hour), excessive cloud API calls to Google Speech Services, network dependency for wake-word, and continuous mic recording without on-device VAD gating.
- **Target Fix (Phase 2):** Strip continuous `SpeechRecognizer` wake-word fallback completely. Primary: Sherpa-ONNX / ONNX KWS; Fallback: lightweight local KWS; Final fallback: Push-to-talk.

### 2.2 Lack of Centralized STT Multi-Engine Cascade
- **Location:** `com/jarvis/assistant/voice/SpeechController.kt`
- **Issue:** STT is hardcoded exclusively to `android.speech.SpeechRecognizer`.
- **Impact:** Offline speech recognition fails on devices without Google Speech Services or offline language packs installed. No streaming partial inference via on-device Sherpa-ONNX.
- **Target Fix (Phase 3):** Scaffold `STTEngine` with `SherpaOnnxSTT` (primary), `AndroidSTT` (fallback), and `RemoteSTTFallback`.

### 2.3 Monolithic TextToSpeech Engine with Missing Controls
- **Location:** `com/jarvis/assistant/voice/TextToSpeechEngine.kt`
- **Issue:** Relies solely on Android system TTS. Lacks `pause()`, `resume()`, `setVoice()`, and `setPitch()`. Holds the TTS engine in memory indefinitely after speaking.
- **Target Fix (Phase 3):** Scaffold `TTSEngine` with `Sherpa/Piper` local neural voices, Android TTS fallback, and resource release hooks after utterance completion.

---

## 3. 🤖 Action Execution, Verification & Automation Flaws

### 3.1 Simulated Action Verification (Deceptive Success)
- **Location:** `com/jarvis/assistant/actionengine/core/ActionExecutor.kt` (line 89)
- **Issue:** `ActionExecutor` sets `verificationPassed = executionSuccess` without verifying device, OS, or app state.
  ```kotlin
  // ActionExecutor.kt Line 89:
  verificationPassed = executionSuccess
  ```
- **Impact:** If an app launch or setting toggle intent fires without error, the assistant reports success even if the app crashed, permission was denied, or the UI did not reach the target state.
- **Target Fix (Phase 8):** Implement state-aware verification: read hardware state (e.g. torch state via CameraManager, volume level via AudioManager, active package via Accessibility/UsageStats) before declaring success.

### 3.2 Blind Delays Instead of State-Aware Synchronization
- **Location:** `com/jarvis/assistant/actionengine/core/ActionExecutor.kt` (lines 124-128)
- **Issue:** Steps waiting for app readiness use hardcoded `delay(1000L)`.
- **Impact:** Flaky automation on slower phones or slow networks; wasted latency on fast phones.
- **Target Fix (Phase 7):** Replace `WAIT` with `WAIT_UNTIL` condition engine (evaluating `package == target`, `node(id) exists`, or `media_state == playing` with timeout and poll intervals).

### 3.3 Stubbed Contact Resolution & Fallback Bypass
- **Location:** `com/jarvis/assistant/actionengine/core/ActionExecutor.kt` (lines 174-177, 202)
- **Issue:** `ActionType.RESOLVE_CONTACT` simply echoes back the contact string without querying Android `ContactsContract`. Unhandled action types return `Pair(true, null)`.
- **Target Fix (Phase 4 & 5):** Route contact resolution through real `ContactsContract` queries and return explicit error codes for unhandled actions.

---

## 4. ☁️ Backend Architecture & Fake Implementations

### 4.1 Insecure Default Authentication Gate (`Missing Auth -> Allow`)
- **Location:** `jarvis/backend/app/security/auth.py` (lines 20-25)
- **Issue:** `validate_ws_token` returns `True` if the environment variable `JARVIS_WS_AUTH_TOKEN` is unset.
  ```python
  def validate_ws_token(token: str) -> bool:
      expected_token = os.getenv("JARVIS_WS_AUTH_TOKEN")
      if not expected_token:
          return True  # VULNERABILITY: Missing auth defaults to allow!
      return token == expected_token
  ```
- **Impact:** Backend WebSocket is open to any unauthenticated client if deployment env var is omitted.
- **Target Fix (Phase 10):** Enforce strict authentication: missing credentials or unset server secrets must reject incoming connections immediately.

### 4.2 Fake Tool Implementations in Backend
- **Location:** `jarvis/backend/app/tools/executor.py` (lines 32-56)
- **Issue:**
  1. `analyze_image` returns a static mock string: `"Multimodal analysis completed for prompt... Image shows UI components and controls."`
  2. `get_battery_level` returns a hardcoded mock value: `"Battery level is 85%"`.
- **Target Fix (Phase 12 & 13):** Route image analysis to real Vision LLM provider (Gemini 1.5 Flash / Groq Llama 3.2 Vision) and query real device telemetry from the Android client instead of mocking.

### 4.3 Optimistic Backend Device Action Pretense
- **Location:** `jarvis/backend/app/tools/executor.py` (lines 86-92)
- **Issue:** For device actions (`toggle_torch`, `toggle_wifi`, `open_app`, etc.), the backend immediately returns `{"status": "success", "dispatch_to_device": True}` before dispatching or receiving confirmation from Android.
- **Impact:** Backend claims action succeeded before the device has even received or executed the payload.
- **Target Fix (Phase 5):** Adopt bidirectional action protocol: Backend issues `ActionRequest`, Android dispatches `ActionStarted`, executes, verifies, and returns `ActionResult` or `ActionFailed`.

### 4.4 Trivial 1-Step Backend Planner & Unstructured Prompting
- **Location:** `jarvis/backend/app/agent/planner.py` (lines 27-34) & `orchestrator.py`
- **Issue:** `TaskPlanner.create_plan` wraps any action into a trivial 1-step array. LLM output parsing relies on unstructured `"output a JSON object"` in system prompt without Pydantic schema validation.
- **Target Fix (Phase 6 & 13):** Replace with structured tool-calling schema validation and DAG-based task planner.

---

## 5. 📦 Packaging, Permissions & Documentation Discrepancies

### 5.1 APK Binary Size Documentation Discrepancy (34 MB vs Claimed 2.8 MB)
- **Location:** `export/jarvis-production-release.apk` (35,641,693 bytes / 34.0 MB) vs `FEATURE_TEST_RESULTS.md` ("2.8 MB")
- **Issue:** The documentation claimed a 2.8 MB release APK. In reality, bundling uncompressed ONNX Runtime `.so` files for both `arm64-v8a` (18.2 MB) and `armeabi-v7a` (13.2 MB) produces a 34 MB APK.
- **Target Fix (Phase 16 & 19):** Align documentation with actual build measurements and implement per-ABI APK splits (`isSplitPerAbi = true`) or arm64-only builds.

### 5.2 Broad `QUERY_ALL_PACKAGES` Permission
- **Location:** `AndroidManifest.xml` (line 31)
- **Issue:** Declares `<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />`.
- **Impact:** Violates Google Play Store package visibility policy and exposes broad app discovery without targeted intent filtering.
- **Target Fix (Phase 14):** Remove `QUERY_ALL_PACKAGES` and scope package visibility via specific `<queries>` intent filters.
