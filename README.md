# JARVIS — Android-First Local AI Assistant

> **A fast, lightweight, always-ready Android AI assistant that listens for "Jarvis" wake phrases, understands speech, controls the device through native APIs and AccessibilityService, executes local tasks, connects to LLM providers, remembers context, and responds by voice.**

---

## 🏛️ System Architecture Overview

```text
               ANDROID CLIENT (Primary Product)
 ┌─────────────────────────────────────────────────────────────┐
 │                                                             │
 │  Microphone → Wake Word → VAD → STT                         │
 │                      ↓                                      │
 │                 VoiceRuntime                                │
 │                      ↓                                      │
 │             JarvisBrain (Local-First)                        │
 │       ┌──────────────┴──────────────┐                       │
 │  Level-1 Local              Level-2 / Level-3 Connected   │
 │  Deterministic              WebSocket API Gateway           │
 │  (Torch, WiFi, Apps,        (Groq, OpenRouter, Gemini,     │
 │   Volume, Screen Read)       Ollama)                        │
 │       └──────────────┬──────────────┘                       │
 │                      ↓                                      │
 │              CommandExecutor                                │
 │       ┌──────────────┴──────────────┐                       │
 │  Device Controllers        Accessibility                    │
 │  (System, Media, Camera)   Service                          │
 │                      ↓                                      │
 │                 TTS Output                                  │
 └────────────────(WebSocket/HTTP)─────────────────────────────┘
                               │
                               ▼
               CONNECTED CLOUD BACKEND (FastAPI)
 ┌─────────────────────────────────────────────────────────────┐
 │  LLM Gateway (Groq, OpenRouter, Gemini, Ollama)              │
 │  Single-Use Security Token Manager                          │
 │  SQLite / PostgreSQL Persistent Store                        │
 └─────────────────────────────────────────────────────────────┘
```

---

## ⚡ Key Capabilities

- **Always-Ready Wake Word**: Local wake-word engine recognizing phrase variants: `Jarvis`, `Hey Jarvis`, `Hay Jarvis`, `Jarvis suno`, `Jarvis listen`, `Jarvis hello`.
- **Level-1 Sub-Second Execution**: Local device control commands (flashlight, Wi-Fi, Bluetooth, volume, app launcher, screen reader, time, battery) execute instantly on-device without remote LLM latency.
- **Resource-Aware Voice Pipeline**: Keeps heavy STT/TTS unmapped during idle states to minimize battery and memory consumption.
- **Accessibility Automation**: `JarvisAccessibilityService` providing safe high-level UI interaction APIs (`tap`, `scroll`, `back`, `home`, `openRecents`, `typeText`, `readScreen`) with automatic password field masking.
- **Multi-Provider LLM Gateway**: Dynamic model discovery for **Groq**, **OpenRouter**, **Google Gemini**, and **Ollama**. Exposes *only authenticated and operational providers* in the UI, supporting live runtime model switching.
- **Single-Use Token Security**: Risky actions (phone calls, SMS, WhatsApp) generate 256-bit entropy random tokens (`secrets.token_urlsafe(32)`) with TTL expiration and replay protection.
- **Persistent Memory**: SQLite database (`persistent_store.py`) or PostgreSQL (`DATABASE_URL`) persisting conversation logs and user facts across restarts.

---

## 📁 Repository Structure

```text
jarvis/
├── android/            # Primary Product: Gradle Android App (Compose UI, Voice Runtime, Accessibility)
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── kotlin/com/jarvis/assistant/
│   │   │   │   ├── app/           # JarvisApplication & AppState
│   │   │   │   ├── voice/         # VoiceRuntime, WakeWordEngine, VadEngine, STT, TTS
│   │   │   │   ├── brain/         # JarvisBrain, IntentResolver, CommandParser, Planner
│   │   │   │   ├── execution/     # CommandExecutor, ToolRegistry, TaskManager
│   │   │   │   ├── accessibility/ # JarvisAccessibilityService, AccessibilityController
│   │   │   │   ├── device/        # SystemController, AppController, MediaController
│   │   │   │   ├── network/       # ApiClient, WebSocketClient, ProtocolModels
│   │   │   │   ├── llm/           # ProviderRegistry, ProviderManager, ModelInfo
│   │   │   │   ├── memory/        # MemoryStore, ConversationMemory, PreferenceMemory
│   │   │   │   ├── permissions/   # PermissionManager & Setup Flow
│   │   │   │   ├── services/      # JarvisForegroundService & BootRecoveryReceiver
│   │   │   │   └── ui/            # MainActivity & Compose UI Screens
│   │   │   └── res/               # Manifest, Strings, Colors, Themes, Config
│   │   └── build.gradle.kts
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── gradlew
│
├── backend/            # Connected Cloud Backend (FastAPI, LLM Gateway, Realtime WS)
│   ├── app/
│   │   ├── main.py                # FastAPI entrypoint, /ws, /health, CORS, correlation IDs
│   │   ├── agent/                 # JarvisBrain, intent_resolver, normalizer, planner
│   │   ├── llm/                   # Base, Groq, OpenRouter, Gemini, Ollama adapters
│   │   ├── realtime/              # Protocol, connection_manager, message_router, ws
│   │   ├── memory/                # Persistent SQLite memory & memory_manager
│   │   ├── tools/                 # Tool registry & tool executor
│   │   └── security/              # Exceptions, auth, token_manager, redaction
│   ├── tests/                     # Comprehensive pytest test suite (100% pass)
│   ├── requirements.txt
│   ├── pyproject.toml
│   ├── Dockerfile
│   └── render.yaml
│
├── docs/               # Technical Documentation
│   ├── architecture.md
│   ├── setup.md
│   ├── voice.md
│   ├── accessibility.md
│   ├── providers.md
│   ├── security.md
│   ├── deployment.md           # Docker, Render, Linux systemd, Windows guides
│   └── apk_connection.md       # Connecting APK to Backend (Emulator, LAN, Render)
│
├── scripts/            # Service runner and test scripts (run_backend.sh, test_all.sh)
├── render.yaml         # Root Render Blueprint specification
├── .gitignore
├── README.md
└── LICENSE
```

---

## 🚀 Quick Start & Deployment Guides

### 📱 1. Android Application (Primary Product)

```bash
cd jarvis/android

# Run Android unit tests
./gradlew test

# Build debug APK
./gradlew assembleDebug
```
Output APK location: `jarvis/android/app/build/outputs/apk/debug/app-debug.apk`

📖 **See [apk_connection.md](jarvis/docs/apk_connection.md) for instructions on connecting your APK to your local PC or Render cloud URL.**

---

### ☁️ 2. Deploying Backend to Render (Cloud Docker)

1. Connect your repository `https://github.com/Minaty001/and9` to Render.
2. Render reads `render.yaml` automatically. Set your API Keys in Render Dashboard (`GROQ_API_KEY`, `OPENROUTER_API_KEY`, `GEMINI_API_KEY`).
3. Render URL: `https://your-service.onrender.com` | WebSocket URL: `wss://your-service.onrender.com/ws`

📖 **See [deployment.md](jarvis/docs/deployment.md) for full Linux systemd, Windows PowerShell, and Docker deployment guides.**

---

## 🔑 Environment Variables & API Key Setup

Set provider keys to activate LLM reasoning capabilities:

```ini
GROQ_API_KEY=gsk_your_groq_key
OPENROUTER_API_KEY=sk-or-v1-your_openrouter_key
GEMINI_API_KEY=AIzaSy_your_gemini_key
OLLAMA_BASE_URL=http://localhost:11434
DATABASE_URL=sqlite:///jarvis_memory.db
```

📖 **See [providers.md](jarvis/docs/providers.md) for API provider setup and dynamic model switching.**

---

## 📄 License

MIT License — Copyright (c) 2026 Jarvis AI Project
