# 🏛️ JARVIS / AND9 — Current Baseline Architecture (Phase 0)

> **Document Version:** 1.0.0 (Phase 0 Freeze)  
> **Date:** 2026-08-24  
> **Scope:** Full System Architecture (Android Client & Python Cloud Backend)

---

## 1. System Topology & Component Layout

```
                  ┌────────────────────────────────────────────────────────┐
                  │                 USER VOICE / INTERACTION               │
                  └───────────────────────────┬────────────────────────────┘
                                              │ (Mic Capture / UI Tap)
                                              ▼
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│ ANDROID CLIENT (com.jarvis.assistant)                                                            │
│                                                                                                  │
│  ┌────────────────────────┐         ┌────────────────────────────────────────────────────────┐   │
│  │ JarvisForegroundService│ ◄────── │ MainActivity / Compose UI / JarvisViewModel             │   │
│  └───────────┬────────────┘ (static)└────────────────────────────────────────────────────────┘   │
│              │                                                                                   │
│              ▼                                                                                   │
│  ┌────────────────────────────────────────────────────────────────────────────────────────────┐  │
│  │ VoiceRuntime (Eagerly Instantiated)                                                        │  │
│  │  ├── LowLatencyAudioCapture (16kHz AudioRecord thread)                                     │  │
│  │  ├── NearFieldAudioProcessor (HPF + Dynamic RMS + SNR Gating + AGC)                        │  │
│  │  ├── VadEngine (Energy + ZCR)                                                              │  │
│  │  ├── LiveKitWakeWordEngine (OnnxWakeWordDetector + Prohibited Continuous STT Fallback)     │  │
│  │  ├── SpeechController (Google Android SpeechRecognizer)                                    │  │
│  │  ├── TextToSpeechEngine (android.speech.tts.TextToSpeech)                                  │  │
│  │  └── AudioRouteManager (Bluetooth SCO + Headset Broadcasts)                                │  │
│  └───────────┬────────────────────────────────────────────────────────────────────────────────┘  │
│              │                                                                                   │
│              ▼ (Command Utterance)                                                               │
│  ┌────────────────────────┐         ┌────────────────────────────────────────────────────────┐   │
│  │ JarvisBrain (Local)    │ ──────► │ MemoryEngine (SQLite db.sqlite3)                       │   │
│  │  ├── IntentResolver    │         │  ├── CAG (RAM Hot Cache + SQLite hash lookup)          │   │
│  │  └── CommandParser     │         │  ├── RAG (Token matching in rag_chunks table)          │   │
│  └───────────┬────────────┘         │  └── MAG (mem_episodes + 3 hardcoded regex facts)       │   │
│              │                      └────────────────────────────────────────────────────────┘   │
│              ▼ (Multi-Step / Remote Plan)                                                        │
│  ┌────────────────────────┐         ┌────────────────────────────────────────────────────────┐   │
│  │ ActionExecutor         │         │ WebSocketClient (OkHttp persistent connection)          │   │
│  │  ├── AppController     │         │  └── wss://and9-1.onrender.com/ws                      │   │
│  │  ├── SystemController  │         └───────────────────────────┬────────────────────────────┘   │
│  │  ├── MediaController   │                                     │                                │
│  │  ├── Adapters (YT, WA) │                                     │                                │
│  │  └── Accessibility     │                                     │                                │
│  └────────────────────────┘                                     │                                │
└─────────────────────────────────────────────────────────────────┼────────────────────────────────┘
                                                                  │ WebSocket (JSON protocol)
                                                                  ▼
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│ CLOUD BACKEND (FastAPI / Python 3.12)                                                            │
│                                                                                                  │
│  ┌────────────────────────────────────────────────────────────────────────────────────────────┐  │
│  │ WebSocket Endpoint (/ws) & Router (Realtime Protocol)                                      │  │
│  │  ├── Auth Gate (validate_ws_token — currently permits missing auth)                        │  │
│  │  └── ConnectionManager (Session registry)                                                  │  │
│  └───────────────────────────────┬────────────────────────────────────────────────────────────┘  │
│                                  │                                                               │
│                                  ▼                                                               │
│  ┌────────────────────────────────────────────────────────────────────────────────────────────┐  │
│  │ JarvisBrain (Server-Side Orchestrator)                                                     │  │
│  │  ├── Level-1 IntentResolver (Deterministic regex/string matching)                          │  │
│  │  ├── Level-2 LLM Gateway (Gemini, Groq, Nvidia, OpenRouter, Ollama)                        │  │
│  │  ├── TaskPlanner (Trivial 1-step array wrapper)                                            │  │
│  │  ├── TokenManager (HMAC single-use confirmation tokens for risky tools)                    │  │
│  │  └── ToolExecutor                                                                          │  │
│  │       ├── WebSearch (DuckDuckGo instant answers)                                           │  │
│  │       ├── SearchMusic (Semantic embedding vector search in ChromaDB/SQLite)                │  │
│  │       ├── AnalyzeImage (🔴 Mock static text response)                                      │  │
│  │       ├── GetBattery (🔴 Mock static 85% response)                                         │  │
│  │       └── Device Tools (🔴 Optimistically returns success before device execution)         │  │
│  └───────────────────────────────┬────────────────────────────────────────────────────────────┘  │
│                                  │                                                               │
│                                  ▼                                                               │
│  ┌────────────────────────────────────────────────────────────────────────────────────────────┐  │
│  │ MemoryManager (SQLite persistent_store.db)                                                 │  │
│  │  └── Conversation history & session memory logging                                         │  │
│  └────────────────────────────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Current Execution & Data Flows

### 2.1 Voice-to-Action Flow
1. **Audio Capture:** `LowLatencyAudioCapture` captures raw PCM chunks (160 samples @ 16 kHz) via `AudioRecord`.
2. **DSP Processing:** `NearFieldAudioProcessor` runs 2nd-order Butterworth HPF, estimates dynamic noise floor, evaluates near-field speech energy, and gates audio.
3. **Wake Detection:** `LiveKitWakeWordEngine` feeds PCM to `OnnxWakeWordDetector` running `hey_jarvis.onnx`. *(If ONNX fails, it falls back to a prohibited continuous SpeechRecognizer loop).*
4. **Handoff:** On detection, `VoiceRuntime` transitions state machine `WAKE` -> `LISTENING`, triggers audio feedback, releases wake mic lock, and activates `SpeechController`.
5. **Speech Recognition:** `SpeechController` invokes Android `SpeechRecognizer`, streams partial results to UI, and delivers final recognized utterance.
6. **Intent Resolution:**
   - Evaluates local `CAG` cache in `MemoryEngine` (< 0.1ms).
   - If missing, checks local `IntentResolver` rules.
   - If multi-step or unknown, forwards to backend via `WebSocketClient` or invokes local `ActionExecutor`.
7. **Action Execution:** `ActionExecutor` executes steps sequentially (e.g. `OPEN_APP`, `TOGGLE_TORCH`, `SEARCH_TEXT`) with retry delays.
8. **Verification:** `ActionExecutor` sets `verificationPassed = executionSuccess` without verifying device state.
9. **Speech Synthesis:** `TextToSpeechEngine` speaks the completion string via Android TTS.

---

## 3. Gap Analysis: Current vs Target 20-Phase Architecture

| Dimension | Current Baseline (Phase 0) | Target State (Phases 1-20) |
| :--- | :--- | :--- |
| **Startup Model** | Monolithic eager instantiation of all voice & TTS components on boot. | Lazy-loaded supervisors: only Wake Detector, VAD, and State Machine active in IDLE. |
| **Wake Word Fallback** | Continuous looping Android SpeechRecognizer (battery & privacy hazard). | Multi-tier: Sherpa-ONNX -> Lightweight local KWS -> Push-to-talk. Zero continuous STT. |
| **STT Layer** | Solely Google Android SpeechRecognizer. | Unified `STTEngine`: Sherpa-ONNX streaming (primary) -> Android STT -> Remote STT. |
| **TTS Layer** | System TextToSpeech held in memory indefinitely. | Unified `TTSEngine`: Sherpa/Piper neural voices -> Android TTS -> Remote TTS with lifecycle hooks. |
| **Device Capabilities** | Scattered controllers with varying ad-hoc calling conventions. | Centralized `DeviceCapabilityManager` exposing `DIRECT`, `ACCESSIBILITY`, `SETTINGS_INTENT`, `USER_CONFIRMATION`, `UNAVAILABLE`. |
| **Action Protocol** | Fire-and-forget; backend claims success before Android execution. | Strict event contract: `ActionRequest` -> `ActionStarted` -> `ActionProgress` -> `ActionResult` / `ActionFailed` with device state payload. |
| **Task Engine** | Hardcoded heuristics + 1-step backend wrapper with blind `delay()`. | Task Graph DAG engine with step dependencies, timeouts, retries, and `WAIT_UNTIL` state conditions. |
| **Verification** | Simulated `verificationPassed = executionSuccess`. | Active verification: reading hardware sensor/API/accessibility state before confirming success. |
| **Failure Recovery** | Basic step retry; task aborts on first unhandled failure. | Multi-tier failure engine: `RETRY` -> `REPLAN` -> `FALLBACK` -> `ASK_USER` -> `ABORT`. |
| **Security** | Missing WS token defaults to allow open access. | Mandatory authentication, short-lived session tokens, request nonce validation, and cryptographic confirmation bindings. |
| **Memory & RAG** | Token overlap matching + 3 hardcoded regex patterns. | Hierarchical memory: Episodic + Semantic + Facts with vector embeddings, recency, and reranking. |
| **Backend Tools** | Static mock strings for image analysis and battery. | Real Vision LLMs, real device telemetry queries, and strict JSON Schema validation with whitelist rejection. |
| **APK Footprint** | 34.0 MB universal APK bundling dual ABIs. | Optimized per-ABI APK splits (arm64-v8a target < 15-20 MB), R8 minification, and model compression. |
