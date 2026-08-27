# JARVIS Android — Research & Feature/Action Expansion Plan

> **Date:** 2026-08-27
> **Scope:** Android client only (`jarvis/android/app/src/main/kotlin/...`)
> **Status:** RESEARCH + PLAN ONLY — no code changes made.
> **Note:** Existing docs `01_FEATURES_DEEPSEARCH.md`, `02_IMPLEMENTED_NOW.md`,
> `03_OPTIONAL_FUTURE.md`, and `FEATURES_AND_FUNCTIONS.md` are **STALE**. The actual
> Android code has moved far beyond them — most "stub" markings are wrong. This plan is
> based on a direct source-read of the current working tree.

---

## PART A — RESEARCH FINDINGS (current real state)

### A.1 What JARVIS Android already does (verified from source)

**Voice pipeline (REAL, not stub):**
- Wake word: `OnnxWakeWordDetector.kt` (offline ONNX KWS) + `WakePhraseMatcher` (8+ English/Hinglish variants) + `WakeCooldown`.
- VAD: `VadEngine.kt` — real energy/RMS + dBFS hysteresis + noise-floor calibration.
- STT: `SpeechRecognizer.kt` → `SpeechController` (Android SpeechRecognizer wrapper, real).
- TTS: `TextToSpeechEngine.kt` — real Android TTS, locale cascade en-IN→default→en-US, utterance callbacks, queue.
- DSP: `NearFieldAudioProcessor.kt` (Butterworth HPF, noise floor, AGC), `LowLatencyAudioCapture`, `AudioRouteManager` (BT SCO), `MicController` (single-owner lock).

**Command brain (REAL):**
- `IntentResolver.kt` — 31 sealed `JarvisIntent` types; rich Hinglish + English keyword matching; 0ms direct-app triggers.
- `CommandExecutor.kt` — routes every intent to real controllers.
- `Planner.kt` / `ResponseGenerator.kt` — deterministic local plan + spoken reply.

**Device controllers (REAL, not stub):**
- `SystemController` — torch, volume, wifi/bt settings, time, battery, storage, openSettings (per-section).
- `AppController` — 70+ app aliases, launch by name/package, category listing, YouTube/Spotify search-play, closeApp (kill + home).
- `MediaController` — play/pause/next/prev/stop via AudioManager key events.
- `CallController` / `SmsController` / `ContactsController` — CALL intent (name→number lookup), SMS send (SmsManager + intent fallback), WhatsApp (api.whatsapp.com deep link), contact resolution.
- `CallLogController`, `GalleryController`, `CameraController` (selfie front-cam), `NotificationController`.

**Accessibility (REAL):**
- `AccessibilityController` — tap/tapById/tapFirstVideoResult/tapAny, scroll, back/home/recents (GLOBAL_ACTION_*), typeText, readScreen (password-masked tree).
- `GestureController`, `ScreenInspector`, `NodeFinder`, `JarvisAccessibilityService`.

**Action engine (REAL — multi-step):**
- `LocalTaskPlanner` — decomposes compound Hinglish/English commands ("camera kholo aur selfie lo", "youtube kholo aur X play", "whatsapp kholo aur X ko Y bhejo", "torch on karo aur volume badhao") into `TaskPlan` with prerequisites + retries.
- `ActionExecutor` — sequential execution with **authoritative verification**, retry/backoff, telemetry via `DiagnosticEventBus`.
- `ActionType` enum — 30+ action types already defined (CLICK, TYPE_TEXT, SWIPE, SCROLL, MAKE_CALL, SEND_MESSAGE, etc.).
- `ActionPolicy` + `ConfirmationManager` — risk-gated (LOW/MEDIUM/HIGH), confirmation tokens.

**Memory (REAL):**
- `MemoryEngine` — CAG (exact+near, SHA-256 + Jaccard/Levenshtein), RAG (token chunks), MAG (episodic + facts) over SQLite (`JarvisMemoryDatabase`).
- `MemoryDecisionRouter`, `MemoryStore`.

**Networking (REAL):**
- `WebSocketClient` — OkHttp WS with ping/auto-reconnect backoff, JSON command protocol.
- `ApiClient`, `BackendHealthManager`, `ConnectionManager` (state machine), `ProtocolModels`.
- `ProviderManager`/`ProviderRegistry` — 5 providers (NVIDIA, Groq, OpenRouter, Gemini, Ollama), model lists.

**Services/UX (REAL):**
- `JarvisForegroundService` (mic FG), `JarvisOverlayService` + floating Compose hologram/mic button, `JarvisQuickTileService`, `JarvisNotificationListenerService`, `BootRecoveryReceiver`.
- 7 Compose screens (Onboarding, Home, Conversation, Providers, Memory, Settings, Settings sub), `PermissionManager` (real checks).

### A.2 Honest gaps (what is NOT real / partial)
1. **Wifi/Bluetooth toggle is SETTINGS-ONLY** — opens the settings panel; does not programmatically flip the radio (no `WifiManager.setWifiEnabled` / `BluetoothAdapter.enable`, which are restricted on modern Android anyway → this is a platform limitation, not a bug).
2. **Cloud brain path (WebSocket → backend LLM) is wired but backend `ToolExecutor` returns mock telemetry** — device actions are performed on-device regardless, so the Android side is fine; the "unknown" intent routing to cloud is real but backend analysis is mocked.
3. **No on-device LLM** — all non-deterministic reasoning goes to cloud. No LiteRT/MediaPipe local model.
4. **No multimodal** — `analyze_image`/vision not present on Android.
5. **No local vector embeddings** — RAG is keyword/token overlap, not semantic.
6. **No barge-in / full-duplex** — voice is half-duplex (listen → speak → listen).
7. **No scheduled tasks / routines / reminders** — `ScheduleCheck` is a stub reply only.
8. **No smart-home / IoT** — `HomeControl` intent just toggles torch (placeholder).
9. **No email/calendar real integration** — `ScheduleCheck` is cosmetic.
10. **Skill replay / trajectory caching** — not implemented (every task re-plans from scratch).
11. **STT is cloud-dependent** — Android `SpeechRecognizer` needs network; no offline Vosk/Whisper.

---

## PART B — PLAN: Features & Actions JARVIS Can Do on Android

Grouped by effort. Each item lists: **what it enables**, **where to add**, **dependencies/risk**.

### B.1 TIER 1 — Quick wins (high value, low effort, mostly intent+controller glue)

| # | Feature / Action | Enables user to say | Files to touch |
|---|---|---|---|
| 1 | **Brightness / Screen control** | "brightness badhao / kam karo / full" | `SystemController.setBrightness()` + `Settings.System.SCREEN_BRIGHTNESS` (WRITE_SETTINGS perm); `IntentResolver` + `JarvisIntent.SetBrightness` + `CommandExecutor` |
| 2 | **Do Not Disturb toggle** | "dnd on/off", "silent mode" | `NotificationManager.setInterruptionFilter()`; new `ActionType.TOGGLE_DND` |
| 3 | **Airplane mode toggle** | "airplane mode on/off" | Settings panel intent (restricted programmatic) or `ACTION_AIRPLANE_MODE_SETTINGS` |
| 4 | **Flashlight brightness / SOS** | "torch blink", "strobe" | `SystemController` timed toggle loop |
| 5 | **Screenshot** | "screenshot lo" | `MediaProjection` flow (needs foreground permission + user consent) — medium |
| 6 | **Open specific settings sections** | "battery settings kholo", "display settings" | Already partially supported (`openSettings(section)`) — extend intent keywords |
| 7 | **Ringer mode (silent/vibrate/normal)** | "silent karo", "vibrate mode" | `AudioManager.setRingerMode()` (already has volume; add mode) |
| 8 | **Read battery % / storage / time as spoken** | already exist; add **"charging hai ya nahi"** | extend `GetBattery` to report charging state via `BatteryManager` |
| 9 | **Calculator / direct app deep links** | "calculator kholo", "calendar me dekho" | Already in `AppController` aliases — confirm + add more (maps, notes, files) |
| 10 | **Toggle auto-rotate / rotation lock** | "rotation lock on/off" | `Settings.System.ACCELEROMETER_ROTATION` |

### B.2 TIER 2 — High-value assistant features (medium effort)

| # | Feature / Action | Enables | Notes / Risk |
|---|---|---|---|
| 11 | **Reminders & alarms** | "8 baje alarm lagao", "mujhe 2 ghante me yaad dilao" | `AlarmManager` + `BroadcastReceiver`; spoken reminder via TTS. New `ActionType.SET_ALARM` / `SET_REMINDER`. Medium. |
| 12 | **Calendar read/create** | "aaj ka schedule batao", "meeting add karo" | `CalendarContract` read (perm) + intent for create. Medium. |
| 13 | **Read/reply to SMS contextually** | "last SMS padho", "X ko reply karo 'ok'" | `SmsController.readSms()` via `Telephony.Sms` + accessibility type for reply. Medium. |
| 14 | **Read unread WhatsApp messages** | "whatsapp unread padho" | Extend `READ_MESSAGES` + notification listener filtering. Low–Med. |
| 15 | **Web search by voice** | "google pe X dhoondo" | `chromeAdapter.openUrlOrSearch` already exists — add `JarvisIntent.WebSearch` intent + cloud `web_search` tool. Low. |
| 16 | **Location / weather** | "weather kya hai", "main kahan hoon" | `FusedLocation` (perm) + weather API or backend. Medium. |
| 17 | **Timer / stopwatch** | "10 minute timer lagao" | `AlarmManager` countdown + TTS alert. Low–Med. |
| 18 | **Copy / paste / clipboard** | "ye copy karo", read clipboard | `ClipboardManager`. Low. |
| 19 | **Bluetooth device connect (specific)** | "earbuds connect karo" | `BluetoothAdapter.getBondedDevices()` + `BluetoothHeadset` profile connect. Medium (API quirks). |
| 20 | **Volume per-stream** | "alarm volume badhao", "ringtone volume" | `AudioManager` STREAM_ALARM / STREAM_RING. Low. |

### B.3 TIER 3 — Advanced / agentic (higher effort, research-backed)

| # | Feature / Action | Enables | Research basis |
|---|---|---|---|
| 21 | **Barge-in / full-duplex voice** | Interrupt JARVIS mid-sentence naturally | Run mic+VAD during TTS; acoustic echo cancellation of own voice; frame-level interrupt. (Run:ai 2026 barge-in research.) High. |
| 22 | **On-device LLM (LiteRT/Gemma/MediaPipe)** | Fully offline reasoning + function-calling | Local 1B–8B model via LiteRT; eliminates cloud cost/latency for routine tasks. (PokeClaw / Google AI Edge.) High. |
| 23 | **Multimodal vision** | "ye kya hai?" pointing camera; "screen samjho" | Camera frame / screenshot → vision LLM (Gemini/backend) or on-device ML Kit. (New `ActionType.ANALYZE_VISION`.) High. |
| 24 | **Skill replay / trajectory caching** | Learns repeated UI flows, 2.4× faster, fewer LLM calls | Compile successful `TaskPlan` trajectories into reusable parameterized skills in SQLite. (SkillDroid arXiv:2604.14872.) High. |
| 25 | **Local vector RAG (sqlite-vec + embeddings)** | Semantic memory recall offline | `sqlite-vec` extension + on-device embedding model (EmbeddingGemma 308M). Replaces token-overlap RAG. Medium–High. |
| 26 | **Smart-home bridge** | "lights off karo", "AC on karo" | Backend `home_assistant` tool + REST; Android intent passthrough. Medium. |
| 27 | **Routines / IFTTT-style automation** | "movie mode" = torch off + wifi on + youtube open | New `RoutineEngine` over existing `ActionType` + scheduler. Medium. |
| 28 | **Offline STT (Vosk/Whisper)** | Voice works with no network | Replace/add to `SpeechRecognizer` with Vosk model. Medium. |
| 29 | **Email integration** | "gmail check karo", "mail bhejo" | Gmail API / intent; backend tool. Medium. |
| 30 | **Cross-app form fill via accessibility** | "instagram me story caption likho" | `typeText` + `tap` orchestration in `ActionExecutor`. Medium. |
| 31 | **Wear OS companion** | Voice from watch | Separate module; big effort. Future. |
| 32 | **Android 16 hardening** | Predictive back, edge-to-edge, Navigation 3, Kotlin 2.4 | Platform compliance (already `enableOnBackInvokedCallback=true`). Maintenance. |

---

## PART C — RECOMMENDED SEQUENCING (plan, not execution)

**Phase 1 (Tier 1, ~1–2 days):** Brightness, DND, ringer mode, airplane/BT panel, screenshot, rotation lock, extended settings intents, battery charging state. Pure `SystemController` + intent glue; no new permissions beyond existing.

**Phase 2 (Tier 2, ~3–5 days):** Reminders/alarms, timer, calendar read, SMS read/reply, web search intent, clipboard, per-stream volume, WhatsApp unread read. Adds `AlarmManager`, `CalendarContract` read, `ClipboardManager`; one new perm (`READ_CALENDAR`).

**Phase 3 (Tier 3 agentic, ongoing):** On-device LLM (22), skill replay (24), local vector RAG (25), barge-in (21), vision (23), smart-home (26), routines (27), offline STT (28). These are architectural; research links already gathered.

---

## PART D — ANDROID CAPABILITY MATRIX (target end-state)

| Domain | Today (real) | After Tier 1+2 | After Tier 3 |
|---|---|---|---|
| Hardware | torch, vol, brightness*, dnd*, rotate*, settings | + brightness/dnd/rotate/screenshot/timer | full |
| Connectivity | wifi/bt *panel* | + airplane panel, BT device connect | programmatic where permitted |
| Apps | open/close/list/play (YT/Spotify) | + calculator/calendar/files deep links | cross-app form fill |
| Comms | call, sms send, whatsapp send | + sms read/reply, whatsapp unread read | email |
| Media | play/pause/next/prev | per-stream volume | — |
| Knowledge | CAG/RAG/MAG (keyword) | web search, calendar, weather* | vector RAG, on-device LLM |
| Vision | — | — | camera/screen vision |
| Automation | multi-step planner (fixed flows) | reminders, routines, timer | skill replay, agentic LLM |
| Voice | wake+VAD+STT+TTS (half-duplex, cloud STT) | offline STT* | barge-in, on-device LLM |

(\* = partial / depends on platform permission model)

---

## PART E — DELIVERABLES OF THIS SESSION

1. This plan document: `jarvis/docs/05_ANDROID_FEATURE_PLAN.md`
2. Corrected understanding: previous feature docs are stale; current Android code is ~80% real, not stub.
3. A concrete, prioritized, Android-only feature/action backlog (32 items across 3 tiers).

**No code was modified.** Implementation is deferred per your instruction ("implement mat karna, sirf plan banao").
