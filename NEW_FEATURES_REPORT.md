# JARVIS — NEW FEATURES RESEARCH & RECOMMENDATION REPORT

> **Prepared:** 2026-08-27 (continuation of session `20260827_025613_a9abb8`)
> **Scope:** Read existing Jarvis docs (features, implementation reality, test results,
> backlog, roadmap) + web research on 2026 state-of-the-art, then report NEW features
> worth adding.
> **Method:** Internal repo docs + 2026 external research (barge-in voice, on-device RAG,
> LLM GUI automation, on-device LLMs, smart-home/Matter, routines).

---

## 0. TL;DR

Jarvis already has a solid *offline-first Android voice assistant* core: a working DSP/voice
engine, CAG/RAG/MAG memory, 16 hardware controllers, a multi-action task engine, and a
real FastAPI backend with 4 live LLM providers. **The biggest gap is not vision or
smart-home — it is that the newest 2026 capabilities (full-duplex barge-in, on-device LLM,
vector RAG, LLM-driven UI automation) are absent or stubbed.**

The highest-value NEW features, ranked:

1. **Full-duplex barge-in** (interrupt the assistant mid-sentence) — the #1 "feels modern" gap.
2. **On-device LLM (Gemma 3n / Qwen3 / LiteRT)** for true offline brain.
3. **Vector RAG via sqlite-vec / EmbeddingGemma** replacing the current keyword-only RAG.
4. **LLM-driven accessibility UI automation** (AutoDroid-style) to replace hardcoded adapters.
5. **Routines + reminders + local scheduling.**
6. **Smart-home bridge (Matter / Home Assistant)** via backend tool-calling.
7. **Multimodal vision (camera/screen → LLM).**
8. **Streaming tokens over WS** (already partially wired).
9. **Cross-device memory + web/PWA dashboard.**
10. **Telemetry/anonymized analytics** to drive routing.

---

## 1. Where Jarvis Stands Today (from repo docs, not research)

Source docs: `FEATURES_AND_FUNCTIONS.md`, `baseline/feature-matrix.md`,
`jarvis/docs/02_IMPLEMENTED_NOW.md`, `FEATURE_TEST_RESULTS.md`, `docs/roadmap.md`,
`jarvis/docs/03_OPTIONAL_FUTURE.md`.

### 1.1 Real / verified (54/54 features claimed PASS in FEATURE_TEST_RESULTS.md)
- **Voice DSP (V1.1–V1.10):** Butterworth HPF, noise-floor tracker, nearest-voice gating,
  dual-metric VAD, AGC, low-latency capture, BT-SCO routing, native STT w/ audio focus,
  ONNX wake-word, runtime state machine.
- **Memory (M2.1–M2.8):** SQLite3, CAG exact/near match, RAG keyword store, MAG episodic +
  fact extraction (regex only), decision router, auto-learn loop.
- **Hardware (D3.1–D3.16):** torch, wifi, bt, volume, battery, storage, app launch/close,
  media keys, call, SMS, WhatsApp, screen reader, gestures, notification reader, QS tile.
- **Networking (N4.1–N4.5):** OkHttp pool, RTT ping, multi-provider fetch, WS keep-alive,
  DuckDuckGo web_search.
- **UI (U5.1–U5.7):** Compose orb HUD, DSP badge, conversation, memory graph, providers,
  settings, onboarding.
- **Task engine (A6.1–A6.8):** local planner, multi-step executor, YouTube/WhatsApp/Chrome/
  Phone adapters, risk policy, Hinglish failure reporter.
- **Backend:** FastAPI, real WS protocol + auth gate, orchestrator (L1→L3), 4 real LLM
  providers (Groq/OpenRouter/Gemini/Ollama HTTP), SQLite/Postgres memory, HMAC token manager.

### 1.2 Known STUB / FAKE / PARTIAL (from baseline/feature-matrix.md & 02_IMPLEMENTED_NOW.md)
These are the "features" that test as PASS but are really placeholders:
- Backend `tools/executor.py`: `analyze_image` and `get_battery_level` return **hardcoded
  mock strings**; device-action protocol reports `success` before device executes.
- Backend `planner.py` returns a trivial 1-step plan (no DAG decomposition).
- Android `VoiceRuntime` eagerly loads heavy engines at IDLE (lifecycle violation).
- MAG fact extraction is **3 regex patterns only** (no semantic extraction).
- RAG is **keyword/token overlap only** — no embeddings/reranker.
- WS auth **defaults to open** when `JARVIS_WS_AUTH_TOKEN` unset (security flaw).
- Android `ProviderManager.kt` is **empty** (blocks compile per 02_IMPLEMENTED_NOW).
- Sherpa-ONNX local streaming STT and Piper/Sherpa neural TTS are **not integrated**.
- Task executor: `verificationPassed = executionSuccess` (fake verification), blind
  `delay()` instead of polling, `ContactResolver` echoes input (stub).

> ⚠️ Note: `02_IMPLEMENTED_NOW.md` (an earlier audit) marks several Android voice/device
> controllers as STUB (log-only), while `FEATURE_TEST_RESULTS.md` marks the same as PASS.
> The two audits disagree — the newer modernization pass (Phase 0–4, Aug 26–27) appears to
> have filled many of them in. Treat the **feature-matrix + 02 docs as the conservative
> truth** and re-verify on-device before claiming PASS externally.

---

## 2. NEW FEATURES — Research-Backed Recommendations (2026)

Each item below: **what it is, why now (2026 evidence), effort, and where it maps in Jarvis.**

### N1. Full-Duplex Barge-In (interrupt mid-response)
- **What:** Keep listening while speaking; cancel the assistant's own TTS out of the mic
  signal (echo cancellation), decide a real interruption, stop TTS within a couple of audio
  frames. Turns walkie-talkie turn-taking into natural conversation.
- **Why now:** RunEdge (2026-07) describes exactly why DIY Whisper+llama.cpp+Piper stacks
  break barge-in and how an on-device C++/full-duplex loop fixes it. Full duplex is the
  single biggest "feels like 2026" gap for a voice agent.
- **Effort:** High (needs full-duplex audio pipeline + echo cancellation + state machine
  change IDLE→WAKE→LISTEN→PROCESS→SPEAK→**INTERRUPT**).
- **Maps to:** `VoiceRuntime.kt` state machine (V1.10), `TextToSpeechEngine.kt`,
  `NearFieldAudioProcessor.kt` (add AEC). **Currently absent.**

### N2. On-Device LLM (true offline brain)
- **What:** Run a small LLM on the phone so the assistant works with no cloud, no keys, no
  internet.
- **Why now:** 2026 is the year this became practical — Gemma 3n / Gemma 4 E2B via
  LiteRT-LM, Qwen3 0.6B/1.7B, Phi-3/4-mini, Llama 3.2 1B all run under ~4 GB RAM
  (local-llms-on-android, AI Edge Gallery, promptquorum 2026 roundup). xda-developers
  (2026) reports users replacing Google Assistant entirely with a local LLM.
- **Effort:** Med–High (model packaging, LiteRT/ONNX runtime, prompt + KV-cache memory).
- **Maps to:** New `brain/OnDeviceLlm.kt`; complements `JarvisBrain` L1–L3 routing.
  Backend already has Ollama provider — mirror locally.

### N3. Vector RAG (sqlite-vec + EmbeddingGemma / Qwen3-Embedding)
- **What:** Replace keyword-overlap RAG with real embeddings + vector search so memory
  retrieval is semantic, not lexical.
- **Why now:** On-device vector DBs are 2026's "needed enabler" (ObjectBox "On-device
  vector databases in 2026"); `sqlite-vec` + `SQLite-vec` tutorial ship on-device RAG;
  EmbeddingGemma (308M) and Qwen3-Embedding 0.6B are MTEB-competitive and fit in a fraction
  of mobile RAM.
- **Effort:** Med (add embeddings table to `JarvisMemoryDatabase.kt`, sqlite-vec ext,
  embed-on-ingest). **Directly fixes the PARTIAL RAG in M2.4.**
- **Maps to:** `MemoryEngine.kt` (M2.4), `JarvisMemoryDatabase.kt` (M2.1).

### N4. LLM-Driven UI Automation (AutoDroid-style)
- **What:** Replace hardcoded per-app adapters (YouTube/WhatsApp/Chrome) and fake
  `ContactResolver` with an LLM that reads the live accessibility node tree and plans
  actions for ANY app.
- **Why now:** AutoDroid (updated 2026-03) is an LLM Android automation framework with
  offline UI exploration + memory synthesis + online execution; paired with MobileRAG /
  AndroidWorld benchmarks, this is now the standard approach. Fixes the STUB
  `AccessibilityController.tap/typeText` and hardcoded adapters (A6.3–A6.6).
- **Effort:** High (snapshot node tree → LLM planner → execute gesture/type; verification
  by re-reading state — also fixes the fake `verificationPassed`).
- **Maps to:** `AccessibilityController.kt`, `LocalTaskPlanner.kt`, `ActionExecutor.kt`.

### N5. Routines, Reminders & Local Scheduling
- **What:** "Remind me to…", "at 8am turn on wifi", recurring routines.
- **Why now:** Core assistant surface users expect (xda-developers 2026 "the boring stuff
  it did really well: timers, weather, music, smart lamp"); unstore.io "Best AI scheduled
  task apps 2026" confirms demand. **Already in repo backlog (03_OPTIONAL_FUTURE Tier 2).**
- **Effort:** Med (scheduler + `AlarmManager`/`WorkManager` + local notifications).
- **Maps to:** New `routines/` module; `JarvisForegroundService` already exists.

### N6. Smart-Home Bridge (Matter 1.3 / Home Assistant)
- **What:** Voice-control lights, switches, scenes via backend tool-calling to Home
  Assistant / Matter hub.
- **Why now:** Home Assistant Local AI Hub + Matter 1.3 is "no longer niche" in 2026
  (vahac 2026); Home Assistant has a dedicated LLM API (developers.home-assistant.io). This
  is the natural first *external* tool surface and reuses the existing tool-registry.
- **Effort:** Med (backend `tools/registry.py` entry + HA REST/Matter client).
- **Maps to:** `backend/app/tools/registry.py` (extend 11→12 tools).

### N7. Multimodal Vision (camera / screen → LLM)
- **What:** "What do you see?" — describe camera feed or current screen via a vision LLM
  (Gemini vision / Gemma 3n native image).
- **Why now:** Gemma 3n and Gemini Live support vision; local-llms-on-android already does
  camera capture + OCR. **Fixes the FAKE `analyze_image` tool in backend executor.py.**
- **Effort:** Med (camera/screen capture → encode → vision provider call).
- **Maps to:** `tools/executor.py:analyze_image` (replace mock), `AccessibilityController`
  screen read.

### N8. Token Streaming over WebSocket
- **What:** Stream LLM tokens live for a real "thinking/typing" UX.
- **Why now:** `LLMProvider.stream()` already exists in backend; `WebSocketClient` stubs
  exist on Android. Mostly wiring. **In repo backlog Tier 1.**
- **Effort:** Low–Med.
- **Maps to:** `realtime/ws.py`, `network/WebSocketClient.kt`.

### N9. Cross-Device Memory + Web/PWA Dashboard
- **What:** Manage memory/context from a browser; sync selected data.
- **Why now:** On-device vector DBs make "sync only selected data" natural (ObjectBox 2026).
  High effort, Tier 2 backlog.
- **Effort:** High.

### N10. Telemetry / Anonymized Analytics
- **What:** Lightweight failure/latency telemetry to improve model routing.
- **Why now:** Tier 3 backlog, Low effort, directly improves N4.3 provider failover.
- **Effort:** Low.

---

## 3. Prioritized Build Order (grounded in BOTH repo gaps and research)

Phase A — **Close the honesty gap first (unblock & de-fake):** fill empty
`ProviderManager.kt`; replace `analyze_image`/`get_battery_level` mocks with real calls;
fix WS auth default; make `ActionExecutor` verify by re-reading state; wire real
Sherpa-ONNX STT + neural TTS. (Repo must-fix, not "new" — but prerequisite.)

Phase B — **New features that are cheap + high-impact:**
1. N8 streaming tokens (Low–Med, already partially wired)
2. N3 vector RAG (Med, fixes PARTIAL RAG, big quality win)
3. N10 telemetry (Low)
4. N5 routines/reminders (Med, top user-demand)

Phase C — **New features that define "modern 2026 assistant":**
5. N1 full-duplex barge-in (High — the headline capability)
6. N2 on-device LLM (Med–High — true offline)
7. N4 LLM UI automation (High — kills hardcoded adapters + fake verification)
8. N6 smart-home bridge (Med)
9. N7 multimodal vision (Med — fixes fake analyze_image)

Phase D — **Stretch:** N9 cross-device memory/PWA, plugins/skill marketplace, Wear OS,
localization/i18n expansion beyond Hinglish, E2E encrypted transcripts.

---

## 4. Research Sources (2026)

- RunEdge.ai — "Barge-in and interruption handling for on-device voice agents" (2026-07)
- ObjectBox — "On-device vector databases in 2026" (sqlite-vec, EmbeddingGemma, Qwen3-Embed)
- SQLite-vec on-device RAG tutorial (YouTube)
- EmergentMind — "AutoDroid: LLM Android Automation" (updated 2026-03) + MobileRAG/AndroidWorld
- local-llms-on-android (GitHub) — Gemma/Qwen/LLaMA on Android via LiteRT/ONNX
- buildfastwithai / promptquorum — "Gemma 4 / local LLM apps Android 2026"
- xda-developers — "Google Assistant is dead, thanks to my local LLM" (2026)
- unstore.io — "Best AI scheduled task apps for Android 2026"
- vahac — "Home Assistant Local AI Hub: Matter 1.3 Guide 2026"
- developers.home-assistant.io — "API for Large Language Models"

---

## 5. Caveats / Honesty Notes

- `FEATURE_TEST_RESULTS.md` claims 54/54 PASS, but `baseline/feature-matrix.md` and
  `02_IMPLEMENTED_NOW.md` flag many of the same items as STUB/FAKE/PARTIAL. **Re-run the
  on-device test suite on a real device before publishing the 100% number externally.**
- Several "new" recommendations (routines, streaming, WhatsApp intent, web_search) already
  appear in `03_OPTIONAL_FUTURE.md` — this report re-ranks them against 2026 external
  evidence and adds the genuinely new 2026 capabilities (barge-in, on-device LLM, vector
  RAG, LLM UI automation, Matter/HA bridge).
- Effort estimates are relative (Low/Med/High) and assume the Android toolchain builds
  (the prior session could not finish the APK compile due to throttled hosts).
