# 🛸 JARVIS AI ASSISTANT — COMPLETE FEATURES & FUNCTIONS INVENTORY

> **Document Version:** 2.1.0  
> **Target OS:** Android 8.0 (API 26) to Android 15 (API 35, 16KB Page Compatible)  
> **Architecture:** Offline-First Edge AGI + Cloud Brain Fallback  
> **Identity:** JARVIS (Created by Minaty)  
> **Session:** 2026-08-27 — Phase 2 additions: RoutineEngine, RoutinesScreen, 20 new backend tools, 2 new intents (ToggleAirplaneMode, GetDailyBriefing), extended settings sections, 13 new unit tests

---

## 1. 🎙️ Acoustic & Voice Processing Engine (DSP & Nearest-Voice)

| # | Feature / Function | File / Class | Description & Operational Flow |
|---|---|---|---|
| **V1.1** | **2nd-Order Butterworth High-Pass Filter (IIR)** | `NearFieldAudioProcessor.kt` | Cuts low-frequency wind noise, mechanical hum, and sub-bass rumble. Dynamic cutoff: **85 Hz (Indoor)** / **135 Hz (Outdoor)**. |
| **V1.2** | **Continuous Adaptive Noise Floor Tracker** | `NearFieldAudioProcessor.kt` | Tracks ambient background noise level between **-75 dBFS (Quiet Indoor)** and **-25 dBFS (Noisy Outdoor / Traffic)** with 98% smoothing. |
| **V1.3** | **Nearest-Voice Proximity Gate (Dominance Filter)** | `NearFieldAudioProcessor.kt` | Discriminates speaker distance using RMS SNR gating (> 12 dB) and high-frequency direct-path energy ratio (> 0.28) to reject distant background speakers. |
| **V1.4** | **Dual-Metric Voice Activity Detector (VAD)** | `NearFieldAudioProcessor.kt` | Combines Zero-Crossing Rate (ZCR 0.05–0.50) and dynamic energy thresholding to detect valid speech frames. |
| **V1.5** | **Zero-Clipping Auto Gain Controller (AGC)** | `NearFieldAudioProcessor.kt` | Automatically normalizes microphone output to target -16 dBFS with peak soft-limiter to prevent distortion. |
| **V1.6** | **Low-Latency Background Audio Capture** | `LowLatencyAudioCapture.kt` | High-priority background worker using `VOICE_RECOGNITION` audio source capturing 16kHz mono PCM frames (10ms / 160 samples per chunk). |
| **V1.7** | **Bluetooth SCO Mic Auto-Detection & Routing** | `AudioRouteManager.kt` | Listens for Bluetooth headset connections (`ACTION_CONNECTION_STATE_CHANGED`, `ACTION_SCO_AUDIO_STATE_UPDATED`), activates Bluetooth SCO mic, and falls back seamlessly to built-in mic. |
| **V1.8** | **Native Speech Recognizer with Audio Focus** | `SpeechRecognizer.kt` | Native STT with `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` to pause background music/media, plus partial utterance recovery fallback. |
| **V1.9** | **Wake Word Engine (Sherpa-ONNX / OnnxWakeWordDetector)** | `LiveKitWakeWordEngine.kt` / `OnnxWakeWordDetector.kt` | Local offline keyword spotting (KWS) for "Hey Jarvis" with Sherpa-ONNX runtime & VAD. |
| **V1.10** | **Voice Runtime State Machine** | `VoiceRuntime.kt` | Manages transitions across `IDLE` -> `WAKE_LISTENING` -> `COMMAND_LISTENING` -> `PROCESSING` -> `SPEAKING` -> `ERROR`. |

---

## 2. 🧠 Unified Memory Engine (CAG + RAG + MAG)

| # | Feature / Function | File / Class | Description & Operational Flow |
|---|---|---|---|
| **M2.1** | **SQLite3 Persistent Database** | `JarvisMemoryDatabase.kt` | App-private storage at `/files/memory/db.sqlite3` with WAL mode and `meta.json` telemetry. Pre-seeded with Minaty profile & system knowledge. |
| **M2.2** | **CAG (Cache Augmented Generation) — Exact Lookup** | `MemoryEngine.kt` | SHA-256 hash lookup of normalized input returning instant cached responses in **< 1 ms**. |
| **M2.3** | **CAG — Fuzzy/Near Lookup** | `MemoryEngine.kt` | Token Jaccard + Levenshtein edit distance matching (> 0.88 similarity) returning near-match answers in **< 5 ms**. |
| **M2.4** | **RAG (Retrieval Augmented Generation) Knowledge Store** | `MemoryEngine.kt` | Multi-chunk local knowledge retrieval (`rag_chunks`) injecting relevant documentation and past learnings into prompts. |
| **M2.5** | **MAG (Memory Augmented Generation) — Episodic History** | `MemoryEngine.kt` | Persists all conversation turns (`mem_episodes`) with timestamp, role, and text. |
| **M2.6** | **MAG — User Profile & Fact Extraction** | `MemoryEngine.kt` | Auto-detects user statements ("My name is...", "I live in...", "Remember that...") and stores in `mem_facts` & `mem_user`. |
| **M2.7** | **Memory Decision Router (Fast/Slow Path)** | `MemoryDecisionRouter.kt` | Calculates complexity score (0.0 to 1.0) to instantly route queries: Fast Local Path (CAG/Rule) vs Slow Path (RAG + MAG + LLM). |
| **M2.8** | **Auto-Learn Loop** | `MemoryDecisionRouter.kt` | Automatically ingests new high-quality answers into CAG, RAG, and MAG so future queries run in 0ms offline! |

---

## 3. 📱 Hardware & Device Automation Controllers

| # | Feature / Function | File / Class | Description & Operational Flow |
|---|---|---|---|
| **D3.1** | **Torch / Flashlight Controller** | `SystemController.kt` | Turns camera torch ON or OFF via `CameraManager.setTorchMode()`. |
| **D3.2** | **Wi-Fi Controller** | `SystemController.kt` | Toggles Wi-Fi state or opens Wi-Fi panel intent on Android 10+. |
| **D3.3** | **Bluetooth Controller** | `SystemController.kt` | Toggles Bluetooth adapter or launches Bluetooth settings intent. |
| **D3.4** | **Volume Controller** | `SystemController.kt` | Sets media/system volume percentage (0–100%) via `AudioManager`. |
| **D3.5** | **Battery Status Reader** | `SystemController.kt` | Reads battery percentage, charging state, and power source via `BatteryManager`. |
| **D3.6** | **Storage Status Reader** | `SystemController.kt` | Calculates total, free, and used internal storage via `StatFs`. |
| **D3.7** | **App Launcher by Name & Aliases** | `AppController.kt` | Queries `PackageManager` with comprehensive aliases dictionary (YouTube, WhatsApp, Chrome, Camera, Instagram, Spotify, etc.). |
| **D3.8** | **App Closer & Home Navigator** | `AppController.kt` | Kills background process or dispatches `GLOBAL_ACTION_HOME` / Home Intent to cleanly exit app. |
| **D3.9** | **Media Transport Controller** | `MediaController.kt` | Dispatches `KEYCODE_MEDIA_PLAY_PAUSE`, `KEYCODE_MEDIA_NEXT`, `KEYCODE_MEDIA_PREVIOUS`, `KEYCODE_MEDIA_STOP` to active music players (YouTube Music, Spotify, Samsung Music, VLC, JioSaavn). |
| **D3.10** | **Phone Call Initiator** | `CallController.kt` | Queries `ContactsContract` for contact phone numbers and triggers `ACTION_CALL` / `ACTION_DIAL`. |
| **D3.11** | **SMS Messenger** | `SmsController.kt` | Sends SMS messages directly via `SmsManager` or `ACTION_SENDTO`. |
| **D3.12** | **WhatsApp Messenger** | `SmsController.kt` | Resolves contact number and opens direct WhatsApp chat via `https://api.whatsapp.com/send` and package targeting. |
| **D3.13** | **Accessibility Screen Reader** | `AccessibilityController.kt` | Recursively reads active interactive screen nodes with password masking. |
| **D3.14** | **Accessibility Gesture Controller** | `GestureController.kt` | Executes programmatic screen `tap(x, y)` and `swipe(x1, y1, x2, y2)` via `dispatchGesture`. |
| **D3.15** | **Notification Reader** | `JarvisNotificationListenerService.kt` | Reads active status bar notifications (app name, title, message content). |
| **D3.16** | **Quick Settings Tile Service** | `JarvisQuickTileService.kt` | Allows users to toggle Jarvis background listening directly from Android Notification Quick Settings shade. |

---

## 4. 🌐 Low-Latency Networking & Backend Gateway

| # | Feature / Function | File / Class | Description & Operational Flow |
|---|---|---|---|
| **N4.1** | **OkHttp Connection Pool (HTTP/2 Multiplexing)** | `ApiClient.kt` | Pre-warmed TLS connections (`ConnectionPool(10, 5, TimeUnit.MINUTES)`) eliminating cold handshake latency. |
| **N4.2** | **Sub-Second RTT Health Ping** | `ApiClient.kt` | Fast backend latency probe with 3.5s timeout measuring live server ping. |
| **N4.3** | **Live Multi-Provider Gateway** | `ApiClient.kt` & `orchestrator.py` | Fetches and switches between Groq, Nvidia NIM, OpenRouter, Gemini, and Ollama. |
| **N4.4** | **Low-Latency WebSocket Streaming** | `WebSocketClient.kt` | Persistent WebSocket client with 8-second keep-alive ping and auto-reconnect backoff. |
| **N4.5** | **Live Web Search Grounding** | `ToolExecutor.py` | DuckDuckGo search integration returning real-time web knowledge. |

---

## 5. 🎨 UI & User Experience (Jetpack Compose)

| # | Feature / Function | File / Class | Description & Operational Flow |
|---|---|---|---|
| **U5.1** | **Cosmic Pulsing Orb HUD** | `HomeScreen.kt` | Animated gradient orb reflecting voice states (Idle, Listening, Thinking, Speaking, Error). |
| **U5.2** | **Acoustic DSP Live Badge** | `HomeScreen.kt` | Real-time telemetry displaying active noise floor (dBFS) and Indoor/Outdoor filter profile. |
| **U5.3** | **Interactive Conversation Screen** | `ConversationScreen.kt` | Real-time chat history with copyable messages, quick action buttons, and status indicator. |
| **U5.4** | **Knowledge & Memory Graph Screen** | `MemoryScreen.kt` | Visualizes stored CAG facts, user profile, and episodic logs with instant search and delete. |
| **U5.5** | **Provider Selection Screen** | `ProvidersScreen.kt` | Live provider cards with health ping metrics, model selectors, and latency stats. |
| **U5.6** | **Settings Screen** | `SettingsScreen.kt` | Controls TTS speech rate, wake sensitivity, backend URL, and permission status. |
| **U5.7** | **Onboarding Screen** | `OnboardingScreen.kt` | Step-by-step setup for microphone, accessibility, and background service permissions. |

---

## 6. ⚡ Multi-Action Task Engine (Task-to-Task Action Architecture)

| # | Feature / Function | File / Class | Description & Operational Flow |
|---|---|---|---|
| **A6.1** | **Task Plan Decomposer & Planner** | `LocalTaskPlanner.kt` | Decomposes composite user commands ("YouTube kholo aur gaana bajao", "WhatsApp kholo aur message bhejo", "Torch on karo aur volume badhao") into multi-step atomic `ActionStep` sequences. |
| **A6.2** | **Multi-Step Action Executor** | `ActionExecutor.kt` | Orchestrates sequential step execution, respects prerequisites, handles step retries with backoff delays, and tracks live `TaskState` (`PLANNING` -> `EXECUTING` -> `VERIFYING` -> `COMPLETED`). |
| **A6.3** | **YouTube Automation Adapter** | `YouTubeAdapter.kt` | Targeted YouTube app search, deep-link video playback, and background media control. |
| **A6.4** | **WhatsApp Automation Adapter** | `WhatsAppAdapter.kt` | Resolves contact phone number from phonebook and launches targeted chat via direct WhatsApp intent with encoded message payload. |
| **A6.5** | **Chrome Web Navigation Adapter** | `ChromeAdapter.kt` | Deep link and web search query dispatching to Chrome. |
| **A6.6** | **Phone & Call Log Adapter** | `PhoneAdapter.kt` | Reads recent call logs and initiates outgoing phone calls. |
| **A6.7** | **Risk Policy & User Confirmation** | `ActionPolicy.kt` | Enforces risk tiers (`LOW`, `MEDIUM`, `HIGH`) requiring explicit confirmation before executing calls or messaging actions. |
| **A6.8** | **Hinglish/English Failure Reporter** | `FailureReporter.kt` | Converts failure codes (`APP_NOT_INSTALLED`, `ELEMENT_NOT_FOUND`, `PERMISSION_DENIED`, `TIMEOUT`) into natural spoken feedback in Hindi & English. |

---

## 7. ⚡ Routine Engine (IFTTT-Style Preset Automation)

| # | Feature / Function | File / Class | Description & Operational Flow |
|---|---|---|---|
| **R7.1** | **RoutineEngine** | `routines/RoutineEngine.kt` | Executes named multi-step preset routines. 7 built-in routines: Morning, Night, Movie, Meeting, Driving, Gym, Reading. Each routine orchestrates multiple device controllers (brightness, volume, DND, ringer, torch, auto-rotate, media, maps) in a single voice command. |
| **R7.2** | **Morning Routine** | `RoutineEngine.executeMorning()` | Brightness → 80%, Volume → 50%, DND off, Ringer normal. Reads time + battery. Voice: "morning routine chalao". |
| **R7.3** | **Night Routine** | `RoutineEngine.executeNight()` | Brightness → 15%, Volume → 20%, DND on, Silent mode. Voice: "night mode on karo". |
| **R7.4** | **Movie Mode** | `RoutineEngine.executeMovie()` | Brightness → 100%, Volume → 80%, DND on, Torch off, Auto-rotate on. Voice: "movie mode chalao". |
| **R7.5** | **Meeting Mode** | `RoutineEngine.executeMeeting()` | DND on, Vibrate mode, Volume → 0, Brightness → 60%. Voice: "meeting mode". |
| **R7.6** | **Driving Mode** | `RoutineEngine.executeDriving()` | Volume → 100%, Brightness → 100%, Auto-rotate on, Maps opened. Voice: "driving mode on". |
| **R7.7** | **Gym Mode** | `RoutineEngine.executeGym()` | Volume → 100%, DND on, Music play. Voice: "gym mode chalao". |
| **R7.8** | **Reading Mode** | `RoutineEngine.executeReading()` | Brightness → 50%, DND on, Silent, Rotation locked portrait. Voice: "reading mode". |
| **R7.9** | **Routines Screen (UI)** | `ui/screens/RoutinesScreen.kt` | 2-column grid Compose UI showing all 7 routines as tappable cards with emoji, description, and voice shortcut. Accessible from bottom nav ⚡ icon. |
| **R7.10** | **Routine Resolution** | `IntentResolver` + `RoutineEngine.resolveRoutineName()` | 60+ Hinglish/English aliases recognized. Any alias (e.g. "movie time", "film mode", "cinema mode") maps to canonical routine name. |

---

## 8. 🌐 Phase 2 Intent Expansions (New 2026-08-27)

| # | Feature / Function | Intent | Description |
|---|---|---|---|
| **P8.1** | **Airplane Mode** | `ToggleAirplaneMode` | Opens Airplane/Flight mode settings panel. Voice: "airplane mode on/off", "flight mode". |
| **P8.2** | **Daily Briefing** | `GetDailyBriefing` | Full spoken morning briefing: time, battery status, storage, calendar events. Voice: "daily briefing sunao", "aaj ka update". |
| **P8.3** | **Extended Settings Sections** | `OpenSettings(section)` | Added: location, security, NFC, storage, airplane mode settings sections. |
| **P8.4** | **Backend Tool Registry +20** | `tools/registry.py` | Added 20 new backend tool definitions: set_brightness, toggle_dnd, set_ringer_mode, toggle_rotation_lock, take_screenshot, run_routine, set_alarm, set_timer, set_reminder, get_location, navigate_to, read_calendar, get_daily_briefing, lock_screen, + more. |
| **P8.5** | **Backend Executor Honesty** | `tools/executor.py` | `analyze_image` and `get_battery_level` now return `pending_device` status with `dispatch_to_device: true` instead of hardcoded mock strings. |

---

## 9. 🌐 Phase 3 Hybrid Connectivity & Voice Pipeline Hardening (2026-08-28)

| # | Feature / Function | File / Class | Description & Operational Flow |
|---|---|---|---|
| **C9.1** | **Online Default Connectivity** | `BackendHealthManager.kt` / `SettingsManager.kt` | Automatically boots in Online mode, monitors Android network callbacks, and maintains live HTTP & WebSocket connections with cloud backend. |
| **C9.2** | **Explicit Offline Mode Toggle** | `HomeScreen.kt` / `SettingsScreen.kt` | Dedicated UI switch on Home & Settings screens to transition JARVIS to 100% on-device local execution without cloud dependency. |
| **C9.3** | **Structured Network Failure & Auth Recovery** | `ApiClient.kt` / `JarvisViewModel.kt` | Introduces `ChatResult` with status codes, token validity checks, and auto-registration recovery. |
| **C9.4** | **Samsung/OEM SpeechRecognizer Teardown Guard** | `SpeechController.kt` | Prevents "not connected" platform crashes during active audio session transitions and cancellations. |
| **C9.5** | **Microphone Ownership Arbitration** | `MicController.kt` / `LiveKitWakeWordEngine.kt` | Verifies component ownership tags so wake word background threads never release speech recognizer mic ownership. |
| **C9.6** | **NVIDIA NIM LLM Cloud Integration** | `llm/providers.py` / `render.yaml` | Deploys NVIDIA Nemotron LLM as priority provider on Render cloud with declarative secrets and token redaction. |
