# JARVIS — Android-First Local AI Assistant

Jarvis is a fast, lightweight, always-ready Android AI assistant that listens for "Jarvis" wake phrases, understands speech, controls your device through native Android APIs and `AccessibilityService`, executes tasks, connects to cloud LLMs (Groq, OpenRouter, Gemini, Ollama), remembers context, and responds by voice.

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
 └─────────────────────────────────────────────────────────────┘
```

---

## 📁 Repository Layout

```text
jarvis/
├── android/         # Primary Product: Gradle Android App (Compose UI, Voice Runtime, Accessibility)
├── backend/         # Optional/Connected Cloud Backend (FastAPI, LLM Gateway, WebSocket)
├── docs/            # Architecture, Setup, Voice, Accessibility, Security, Deployment Docs
├── scripts/         # Backend runner and test scripts
├── .gitignore
├── README.md
└── LICENSE
```

---

## 🚀 Quick Start

### Android Application
```bash
cd android
./gradlew test
./gradlew assembleDebug
```

### Backend & Pytest
```bash
cd backend
pip install -r requirements.txt
pytest tests/ -v
python -m uvicorn app.main:app --reload
```
