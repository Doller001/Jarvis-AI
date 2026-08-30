# JARVIS — Cognitive Voice Assistant for Android & Cloud Brain

JARVIS is an intelligent, always-connected cognitive voice assistant built with **Jetpack Compose (Android)** and **FastAPI (Cloud Backend)**. It operates in **Online Always-Connected Mode**, routing complex conversational reasoning to Cloud LLMs (NVIDIA Nemotron, Groq LLaMA 3.3, OpenRouter, Gemini, Ollama) via an auto-reconnecting backend gateway, while executing deterministic hardware actions and wake-word detection directly on-device with zero friction.

---

## 🌟 Core System Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            JARVIS SYSTEM OVERVIEW                           │
├─────────────────────────────────────────────────────────────────────────────┤
│  ANDROID CLIENT (Kotlin / Compose)                                          │
│  ├─ Wake-Word Engine (Sherpa-ONNX "Hey Jarvis" on-device KWS)                │
│  ├─ Voice FSM & Audio Capture (VadEngine, SpeechRecognizer, TTS)            │
│  ├─ Local Task Planner & Action Adapters (Calls, SMS, WhatsApp, System, Media)│
│  ├─ Three-Tier Memory (CAG Fast Hash, RAG SQLite Retrieval, MAG Long-term)  │
│  └─ Backend Health Manager & Live WebSocket Connection                      │
│                                      │                                      │
│                                 HTTPS / WSS                                 │
│                                      ▼                                      │
│  BACKEND CLOUD BRAIN (FastAPI / Python)                                     │
│  ├─ API Gateway (JWT Auth, Device Registry, Health Probes)                  │
│  ├─ Realtime WebSocket Router & Canonical Action Protocol                   │
│  ├─ Multi-Provider LLM Gateway (NVIDIA, Groq, OpenRouter, Gemini, Ollama)   │
│  ├─ Multi-Action Orchestrator & Tool Registry                               │
│  └─ Supabase / Persistent Vector Store & Memory Sync                        │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 🚀 Features & Capabilities

### 🌐 Always-Connected Backend Gateway
- **Default Endpoint:** `https://jarvis-ai-59qd.onrender.com` (configurable in Settings)
- **Automatic Health Probing:** Continuous 30-second ping cycles and instant network restoration reconnection.
- **WebSocket Streaming:** Real-time bidirectional telemetry, action dispatch, and state synchronization.
- **JWT Device Authentication:** Automated cryptographic device registration and session token management.

### 🧠 Cloud Brain & Multi-Provider LLM
- **NVIDIA Nemotron / LLaMA-3.3 70B** for deep cognitive reasoning and complex multi-step planning.
- **Groq, OpenRouter, Gemini, Ollama** seamless fallback with circuit breakers and retry policies.
- Dynamic provider and model switching from the on-device Providers screen.

### 🎤 Voice & Wake-Word System
- **Wake Word Detection:** On-device neural inference using ONNX Runtime Mobile (`melspectrogram.onnx` + `hey_jarvis.onnx`).
- **Push-to-Talk & Continuous Listening:** Voice State Machine with barge-in interruption detection.
- **Speech Synthesis (TTS):** Locale-aware voice synthesis with configurable speech rates (0.8x to 1.5x).

### 📱 Device Control & Hardware Action Engine
- **Volume & Brightness:** Deterministic percentage controls and quick toggles.
- **Connectivity:** Wi-Fi, Bluetooth, Hotspot, and DND mode management.
- **Communication:** Smart phone dialing (speakerphone default), SMS composer, and WhatsApp direct messaging with confirmation gates.
- **System Utilities:** Alarms, countdown timers, calendar briefing, screenshot capture, clipboard management, flashlight, and app launching across 70+ apps.
- **OEM Survival Optimization:** Background survival profiles for Xiaomi/MIUI, Samsung, OnePlus/Oppo, Vivo, and Huawei.

### 💾 Three-Tier Cognitive Memory
1. **CAG (Context-Aware Cache):** 0ms instant response retrieval via SHA-256 and Jaccard token similarity.
2. **RAG (Retrieval-Augmented Generation):** Local SQLite indexed knowledge chunks.
3. **MAG (Long-Term Associative Memory):** User facts, preferences, and episode logs automatically enriched from cloud responses.

---

## 🛠️ Project Structure

```
and9/
├── jarvis/
│   ├── android/              # Jetpack Compose Android Client (com.jarvis.assistant)
│   │   ├── app/
│   │   │   ├── src/main/kotlin/com/jarvis/assistant/
│   │   │   │   ├── actionengine/    # Action planners, adapters (WhatsApp, YouTube, Phone)
│   │   │   │   ├── brain/           # Intent resolver, planner, response generator
│   │   │   │   ├── device/          # System, media, display, app, and alarm controllers
│   │   │   │   ├── memory/          # Three-tier memory engine (CAG/RAG/MAG SQLite)
│   │   │   │   ├── network/         # ApiClient, WebSocketClient, BackendHealthManager, Auth
│   │   │   │   ├── permissions/     # PermissionManager & runtime permission workflows
│   │   │   │   ├── services/        # Foreground service, Overlay, Notifications, Receivers
│   │   │   │   ├── settings/        # SettingsManager (Backend URL, TTS, Wake Word)
│   │   │   │   ├── ui/              # Compose screens (Home, Settings, Providers, Memory, Routines)
│   │   │   │   └── voice/           # VoiceStateMachine, SpeechController, OnnxWakeWordDetector
│   │   │   └── src/main/assets/     # ONNX wake-word neural models
│   │   └── keystore/                # Release signing keystore
│   ├── backend/              # Cloud Brain (FastAPI / Python 3.12)
│   │   ├── app/
│   │   │   ├── agent/        # Execution orchestrator & intent resolver
│   │   │   ├── api/          # FastAPI routes, OpenAPI schema, auth endpoints
│   │   │   ├── db/           # Supabase client & schemas
│   │   │   ├── llm/          # Multi-provider gateway (NVIDIA, Groq, OpenRouter, Gemini, Ollama)
│   │   │   ├── memory/       # Persistent store & memory manager
│   │   │   ├── realtime/     # WebSocket endpoint & canonical action dispatch
│   │   │   ├── security/     # Device registry, JWT manager, capability checks
│   │   │   └── tools/        # Tool registry & execution engine
│   │   ├── tests/            # Pytest test suite (100% passing)
│   │   └── Dockerfile        # Container deployment spec
│   ├── shared/               # Shared protocol schemas & personality specs
│   ├── webapp/               # Web client interface
│   ├── scripts/              # Build, test, and verification automation scripts
│   └── render.yaml           # Render deployment configuration
└── docs/                     # Comprehensive architecture and API documentation
```

---

## ⚡ Quick Start & Verification

### Running Backend Tests
```bash
cd jarvis/backend
pytest
```

### Running Backend Locally
```bash
cd jarvis/backend
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

### Building & Testing Android Application
```bash
cd jarvis/android
./gradlew testDebugUnitTest
./gradlew assembleRelease
```

---

## 🔒 Security & Privacy
- Zero plaintext credential storage: Auth tokens stored in encrypted SharedPreferences (`EncryptedSharedPreferences`).
- Sensitive screen text redaction when using accessibility automation.
- Explicit user confirmation gates for high-impact actions (calling contacts, sending messages).
