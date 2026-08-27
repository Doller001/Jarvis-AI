# JARVIS — Features, Functions & Actions Report

> **Date:** 2026-08-27
> **Scope:** Android client + FastAPI backend (full JARVIS system)
> **Basis:** Direct source-read of current working tree (NOT the stale 01/02/03 docs)
> **Purpose:** Catalog what JARVIS does, what's implemented vs missing, and what's NECESSARY vs OPTIONAL to add.

---

## 1. EXECUTIVE SUMMARY

JARVIS is an **Android-first, local-first AI voice assistant** with a cloud LLM backend.
The Android client is **~80% functionally implemented** (real device control, real voice
pipeline, real multi-step automation). The **backend is ~70% real** but several tools
return **mocked telemetry** (`analyze_image`, `get_battery_level` return hardcoded strings).

**Verdict:**
- ✅ Core voice assistant (wake → understand → act → speak) is REAL and works.
- ⚠️ Several "actions" open settings panels instead of truly toggling (wifi/bt — platform-limited).
- ❌ No on-device LLM, no vision, no barge-in, no reminders/routines, no smart-home.
- 🔧 Backend `ToolExecutor` mocks device telemetry — but Android performs real actions anyway.

---

## 2. FEATURES & FUNCTIONS & ACTIONS — CURRENT STATE

Legend: ✅ Real/Implemented · 🟡 Partial/Placeholder · ❌ Missing/Stub

### A. VOICE & AUDIO PIPELINE
| Function / Action | State | Notes |
|---|---|---|
| Wake word detection ("Jarvis", Hinglish variants) | ✅ | `OnnxWakeWordDetector` (offline ONNX) + `WakePhraseMatcher` (8+ variants) + cooldown |
| Voice Activity Detection (VAD) | ✅ | `VadEngine` — real RMS/dBFS energy + hysteresis + noise calibration |
| Speech-to-Text (STT) | ✅ (cloud) | `SpeechController` → Android SpeechRecognizer (needs network) |
| Text-to-Speech (TTS) | ✅ | `TextToSpeechEngine` — real Android TTS, en-IN cascade, callbacks |
| DSP (HPF, noise floor, AGC) | ✅ | `NearFieldAudioProcessor` |
| Bluetooth SCO mic routing | ✅ | `AudioRouteManager` |
| Single-mic ownership lock | ✅ | `MicController` |
| **Barge-in / full-duplex** | ❌ | Half-duplex only (listen→speak→listen) |
| **Offline STT (Vosk/Whisper)** | ❌ | Cloud-dependent STT |

### B. DEVICE HARDWARE CONTROL
| Function / Action | State | Notes |
|---|---|---|
| Flashlight / Torch on-off | ✅ | `SystemController.toggleTorch` (CameraManager) |
| Volume set (0-100%) | ✅ | `SystemController.setVolume` (AudioManager) |
| **Brightness control** | ❌ | Not implemented (easy add) |
| **Do Not Disturb toggle** | ❌ | Not implemented (easy add) |
| **Ringer mode (silent/vibrate)** | ❌ | Not implemented (easy add) |
| **Screen rotation lock** | ❌ | Not implemented (easy add) |
| **Screenshot** | ❌ | Needs MediaProjection (medium) |
| Wi-Fi toggle | 🟡 | Opens Wi-Fi **settings panel** (cannot programmatically toggle on modern Android) |
| Bluetooth toggle | 🟡 | Opens BT **settings panel** (platform-restricted) |
| **Airplane mode** | ❌ | Not implemented (settings panel only) |
| Battery level read | ✅ | `SystemController.getBatteryLevel` (real) |
| Storage info read | ✅ | `SystemController.getStorageInfo` (real) |
| Time/date read | ✅ | `SystemController.getTime` (real) |

### C. APP CONTROL & AUTOMATION
| Function / Action | State | Notes |
|---|---|---|
| Open app by name/alias | ✅ | `AppController` — 70+ aliases (YouTube, WhatsApp, Chrome, Camera, etc.) |
| Close app / go home | ✅ | `AppController.closeApp` (kill + home) |
| List installed apps (by category) | ✅ | `AppController.getAppsByCategory` |
| Play media on YouTube/Spotify | ✅ | `AppController.playMediaOnApp` |
| Multi-step task planning | ✅ | `LocalTaskPlanner` — compound Hinglish/English ("camera kholo aur selfie lo") |
| Multi-step execution + verification | ✅ | `ActionExecutor` — retries, verification, telemetry |
| Tap/scroll/type/read screen (accessibility) | ✅ | `AccessibilityController` (password-masked) |
| Gesture/swipe | ✅ | `GestureController` |
| **Cross-app form fill** | ❌ | Can tap/type but no orchestrated flow builder |
| **Routines / IFTTT** | ❌ | No routine engine |
| **Skill replay (learn flows)** | ❌ | Every task re-plans from scratch (SkillDroid-style missing) |

### D. COMMUNICATIONS
| Function / Action | State | Notes |
|---|---|---|
| Make phone call (name→number) | ✅ | `CallController.makeCall` + contact lookup |
| Send SMS | ✅ | `SmsController.sendSms` (SmsManager + fallback) |
| Send WhatsApp message | ✅ | `SmsController.sendWhatsApp` (deep link) |
| Read call log | ✅ | `CallLogController.getRecentCalls` |
| Read notifications | ✅ | `NotificationController` + listener service |
| **Read SMS inbox** | ❌ | Send only; read not implemented |
| **Reply to SMS/WhatsApp contextually** | ❌ | Not implemented |
| **Read WhatsApp unread** | 🟡 | Partial — notification-based only |
| **Email (Gmail)** | ❌ | Not implemented |

### E. MEDIA & CAMERA
| Function / Action | State | Notes |
|---|---|---|
| Play / pause / next / prev / stop | ✅ | `MediaController` (key events) |
| Open camera / take selfie | ✅ | `CameraController` (front-cam selfie) |
| Open gallery | ✅ | `GalleryController` |
| **Per-stream volume (alarm/ring)** | ❌ | Only media stream |
| **Camera vision / describe scene** | ❌ | No multimodal |

### F. KNOWLEDGE & MEMORY
| Function / Action | State | Notes |
|---|---|---|
| CAG (instant answer cache) | ✅ | `MemoryEngine.cagExactLookup` (SHA-256, <0.1ms) |
| CAG near-match | ✅ | Jaccard + Levenshtein (>0.88) |
| RAG (retrieval) | 🟡 | Keyword/token overlap only — **no vector embeddings** |
| MAG (episodic + facts) | ✅ | `MemoryEngine` — episodic history, user facts |
| Cross-device memory (Supabase) | ✅ | Backend `persistent_store` (Postgres/Supabase) |
| **Local vector RAG (sqlite-vec)** | ❌ | Keyword-only today |
| **Web search grounding** | 🟡 | Backend `web_search` tool REAL (DuckDuckGo); Android intent missing |

### G. CLOUD BRAIN & LLM
| Function / Action | State | Notes |
|---|---|---|
| Multi-provider gateway (Groq/OpenRouter/Gemini/Ollama/NVIDIA) | ✅ | `ProviderManager` + backend `gateway.py` |
| WebSocket realtime client | ✅ | `WebSocketClient` — OkHttp, ping, auto-reconnect |
| Risk-gated confirmation tokens | ✅ | `ConfirmationManager` + `ActionPolicy` |
| **On-device LLM (offline reasoning)** | ❌ | All non-deterministic → cloud |
| **Structured output / JSON schema validation** | 🟡 | Backend relies on text-JSON prompt, not schema-validated |
| Backend `analyze_image` | ❌ (mock) | Returns hardcoded string |
| Backend `get_battery_level` | ❌ (mock) | Returns hardcoded "85%" |

### H. SERVICES & UX
| Function / Action | State | Notes |
|---|---|---|
| Foreground mic service | ✅ | `JarvisForegroundService` |
| Floating overlay (hologram + mic) | ✅ | `JarvisOverlayService` + Compose UI |
| Quick-settings tile | ✅ | `JarvisQuickTileService` |
| Notification listener | ✅ | `JarvisNotificationListenerService` |
| Boot auto-start | ✅ | `BootRecoveryReceiver` |
| 7 Compose screens | ✅ | Onboarding, Home, Conversation, Providers, Memory, Settings |
| Permission manager | ✅ | `PermissionManager` (real checks) |

---

## 3. WHAT JARVIS CAN DO TODAY (User-Facing Capabilities)

A user can already say (Hinglish or English):
- "Jarvis torch on karo" → flashlight on
- "Volume 50% karo" / "volume badhao" → set volume
- "YouTube kholo aur Believer play karo" → opens YT, searches, plays
- "WhatsApp mummy ko Good morning bhejo" → opens WhatsApp chat with message
- "Call papa" → calls (after confirmation)
- "SMS bhejo" → sends SMS
- "Time kya hai" / "battery kitni hai" → spoken answer
- "Screen padho" → reads active screen
- "Camera kholo aur selfie lo" → multi-step: opens cam + captures
- "App list dikhao" / "music apps kholo"
- "Notification padho" → reads notifications
- "Systems check" → battery/time/storage report
- "Jarvis kon ho?" → persona reply
- "Chrome kholo aur weather dhoondo" → web search

---

## 4. WHAT'S MISSING (Gap Analysis)

### 4.1 NECESSARY (Jaruri) — core assistant expectations, should be added
These are table-stakes for a usable voice assistant and are low/medium effort:

| # | Missing Feature | Why Necessary | Effort |
|---|---|---|---|
| N1 | **Reminders & Alarms** | "8 baje utha do" — basic assistant duty | Med |
| N2 | **Web search by voice** | "google pe X dhoondo" — grounding, already half-done | Low |
| N3 | **Read SMS inbox** | Users expect to hear messages | Med |
| N4 | **Timer / Stopwatch** | "10 min timer lagao" — common command | Low-Med |
| N5 | **Brightness control** | "screen dim karo" — frequent request | Low |
| N6 | **Ringer/DND mode** | "silent karo" — basic control | Low |
| N7 | **Per-stream volume** | "alarm volume badhao" | Low |
| N8 | **Battery charging state** | "charging hai?" — trivial extension | Low |
| N9 | **Fix backend mocks** (`analyze_image`, `get_battery_level`) | Honesty/correctness | Low |
| N10 | **Structured output validation** (backend orchestrator) | Reliability of tool calls | Med |

### 4.2 OPTIONAL (Choice) — nice-to-have, advanced, or research-stage
Higher effort or not expected from a v1 assistant:

| # | Optional Feature | Value | Effort |
|---|---|---|---|
| O1 | On-device LLM (LiteRT/Gemma) | Full offline mode | High |
| O2 | Barge-in / full-duplex voice | Natural conversation | High |
| O3 | Multimodal vision (camera/screen→LLM) | "ye kya hai?" | High |
| O4 | Skill replay / trajectory learning | 2.4× faster repeats (SkillDroid) | High |
| O5 | Local vector RAG (sqlite-vec) | Semantic memory | Med-High |
| O6 | Smart-home / IoT bridge | Lights, AC control | Med |
| O7 | Routines / IFTTT automation | "movie mode" | Med |
| O8 | Offline STT (Vosk/Whisper) | Voice without network | Med |
| O9 | Email (Gmail) integration | Another comms surface | Med |
| O10 | Calendar create/read | Scheduling | Med |
| O11 | Wear OS companion | Watch control | Very High |
| O12 | Cross-app form-fill flows | Complex automation | Med |
| O13 | Screenshot capture | Utility | Med |
| O14 | Airplane mode toggle | Utility (settings panel) | Low |
| O15 | Rotation lock | Utility | Low |
| O16 | Clipboard copy/paste | Utility | Low |

---

## 5. RECOMMENDATION: WHAT TO ADD FIRST

**Necessary (do first — fills core gaps):**
N5 Brightness → N6 Ringer/DND → N7 Per-stream volume → N8 Charging state →
N2 Web search → N4 Timer → N1 Reminders → N3 SMS read → N9/N10 backend fixes.

**Optional (phase 2+):**
O14/O15/O16 utilities → O5 vector RAG → O7 routines → O8 offline STT →
O1 on-device LLM → O3 vision → O2 barge-in → O4 skill replay → O6 smart-home.

---

## 6. ARCHITECTURE NOTES (for implementers)

- **Android action flow:** `IntentResolver` (31 intents) → `CommandExecutor` →
  real controllers OR `LocalTaskPlanner` → `ActionExecutor` (multi-step + verification).
- **Backend action flow:** `intent_resolver.py` (L1) → `orchestrator.py` (L2/L3 LLM) →
  `ToolExecutor` (mocks telemetry, dispatches to device). Device actions are REAL on Android.
- **Risk model:** `ActionType` enum has LOW/MEDIUM/HIGH + `requiresConfirmation`.
  Calls/SMS/WhatsApp already gated.
- **Adding a new action** = (1) `JarvisIntent` sealed subclass, (2) `IntentResolver` rule,
  (3) `CommandExecutor` branch, (4) optional `ActionType` + planner flow. ~4 touch points.

---

*Research complete. No code modified — this is a report. See also:
`05_ANDROID_FEATURE_PLAN.md` (prioritized Android action plan) and
`04_NEW_FEATURES_RESEARCH.md` (2025-2026 emerging-feature research).*
