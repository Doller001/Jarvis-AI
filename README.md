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

## 📋 Environment Prerequisites

Before building the Android app or running backend services, ensure your system has the following tools installed:

1. **Java JDK 17**: `sudo apt-get install openjdk-17-jdk -y`
2. **Gradle**: `sudo apt-get install gradle -y`
3. **Android SDK (API 34)**: Set `ANDROID_HOME=$HOME/Android/Sdk`
4. **Python 3.10+**: For the FastAPI backend server

📖 **See [jarvis/docs/setup.md](jarvis/docs/setup.md) for full step-by-step setup instructions for Linux, macOS, and Windows.**

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

📖 **See [jarvis/docs/apk_connection.md](jarvis/docs/apk_connection.md) for instructions on connecting your APK to your local PC or Render cloud URL.**

---

### ☁️ 2. Deploying Backend to Render (Cloud Docker)

1. Connect your repository `https://github.com/Minaty001/and9` to Render.
2. Render reads `render.yaml` automatically. Set your API Keys in Render Dashboard (`GROQ_API_KEY`, `OPENROUTER_API_KEY`, `GEMINI_API_KEY`).
3. Render URL: `https://your-service.onrender.com` | WebSocket URL: `wss://your-service.onrender.com/ws`

📖 **See [jarvis/docs/deployment.md](jarvis/docs/deployment.md) for full Linux systemd, Windows PowerShell, and Docker deployment guides.**

---

## 📄 License

MIT License — Copyright (c) 2026 Jarvis AI Project
