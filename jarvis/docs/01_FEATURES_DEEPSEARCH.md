# Jarvis — Feature Capability Map (Deep-Research)

> **How this was produced (honesty note):** The live external web-search tool is
> **not available** in this environment (no Firecrawl credits configured), so a
> live internet crawl could not be run. Instead this map was built by a
> **deep source-read across every platform in this repo** (Android Kotlin client,
> FastAPI Python backend, shared `schema.json` protocol, and all existing docs)
> plus **established domain knowledge** of flagship assistants (Google Assistant,
> Siri, Gemini Live, Home Assistant, Tasker/AutoVoice, Apple Intelligence).
>
> Every row is tagged:
> - **Source**: `Repo-arch` (derivable from this repo's architecture/code) or
>   `Domain` (general best-practice for this class of assistant).
> - **Platform**: `Android` / `Backend` / `Both` / `Cross`.
> - **Feasibility**: `Now` (arch already supports it), `Near` (needs glue/wiring),
>   `New` (needs new modules).

---

## Executive Summary

Jarvis is architected as an **Android-first, local-first AI assistant** with a
cloud "brain" backend. The architecture already supports a very broad surface:

- **Local-first voice pipeline** (wake word → VAD → STT → brain → TTS) on Android.
- **On-device deterministic command resolution** (fast path, no network) + **LLM
  reasoning fallback** in the backend.
- **Accessibility-based UI automation** (tap/scroll/type/read screen).
- **Multi-provider LLM gateway** (Groq, OpenRouter, Gemini, Ollama) with circuit
  breaker + retry + failover.
- **Tool registry + risk-gated execution** (safe commands auto-run; risky ones —
  call/SMS/WhatsApp — require a single-use confirmation token).
- **Persistent memory** (SQLite locally, Postgres/Supabase in cloud).
- **Security layer**: single-use tokens, replay protection, log redaction.

The gap between "architecturally supported" and "working end-to-end" is mostly in
**device-action execution and real ML models**: device controllers and voice ML
leaves are currently stubs (log-only), and the backend `ToolExecutor` returns a
mock result (actual device action is meant to happen on-device). See
`02_IMPLEMENTED_NOW.md` for the precise current state.

The capability map below shows what Jarvis **should / can** support given this
foundation.

---

## 1. Voice & Conversation

| Capability | Platform | Source | Feasibility | Notes |
|---|---|---|---|---|
| Always-on wake word ("Jarvis", Hinglish variants) | Android | Repo-arch | Now | `WakeWordEngine` already matches 8 phrase variants w/ cooldown |
| Hands-free continuous conversation (no button) | Android | Repo-arch | Near | `VoiceRuntime` state machine exists; needs real STT/TTS leaves |
| Barge-in / mid-speech interruption | Android | Domain | New | Common in Gemini Live / Siri; not modeled |
| Multi-turn context memory in conversation | Backend | Repo-arch | Now | `MemoryManager` + `persistent_store` persist per-session |
| Streaming LLM responses (token-by-token) | Backend | Repo-arch | Near | `LLMProvider.stream()` defined; not yet plumbed to WS |
| Natural-language command parsing (Hinglish) | Both | Repo-arch | Now | `IntentResolver` (backend) + `JarvisIntent` (Android) handle Hinglish |
| Intent confirmation for risky actions | Both | Repo-arch | Now | `ConfirmationTokenManager` single-use tokens (300s TTL) |

## 2. Device Control (Android)

| Capability | Platform | Source | Feasibility | Notes |
|---|---|---|---|---|
| Toggle Wi-Fi / Bluetooth / Torch | Android | Repo-arch | Near | `SystemController` + `JarvisIntent.ToggleWifi/Bluetooth/Torch` exist; bodies are stubs |
| Set media volume | Android | Repo-arch | Near | `SetVolume` intent + `SystemController.setVolume` stub |
| Open any app by name | Android | Repo-arch | Near | `OpenApp` intent parsed; launcher call not implemented |
| Read current time / battery | Android | Repo-arch | Near | `GetTime`/`GetBattery` intents exist; return hardcoded strings today |
| Media play / pause | Android | Repo-arch | Near | `MediaController` stub |
| Camera open / photo capture | Android | Repo-arch | Near | `CameraController` stub (needs CameraX) |
| Read notifications | Android | Repo-arch | Near | `NotificationController` stub (needs NotificationListenerService) |

## 3. Accessibility & UI Automation

| Capability | Platform | Source | Feasibility | Notes |
|---|---|---|---|---|
| Tap UI element by text / view-id | Android | Repo-arch | Near | `AccessibilityController.tap/tapById` stub (log-only) |
| Scroll, back, home, recents | Android | Repo-arch | Now | `back()/home()/openRecents()` use real `GLOBAL_ACTION_*` when service bound |
| Type text into focused field | Android | Repo-arch | Near | `typeText` stub |
| Read active screen (password-masked) | Android | Repo-arch | Near | `readScreen()` stub; `ScreenInspector` inspects node tree; password masking claimed |
| App-level macro / flow automation | Android | Domain | New | Tasker-style; needs a flow engine on top of accessibility |

## 4. Communications (already risk-gated)

| Capability | Platform | Source | Feasibility | Notes |
|---|---|---|---|---|
| Make phone call by name/number | Android | Repo-arch | Near | `CallContact` intent + `CallController` stub |
| Send SMS | Android | Repo-arch | Near | `SendSms` intent + `SmsController` stub |
| Send WhatsApp message | Backend | Repo-arch | Near | `whatsapp_send` in backend tool registry; Android side not yet wired |
| Read/reply to messages contextually | Both | Domain | New | Requires SMS/notification read + context injection |

## 5. Smart Home / IoT

| Capability | Platform | Source | Feasibility | Notes |
|---|---|---|---|---|
| Control lights/switches (Hue, etc.) | Cross | Domain | New | Not in repo; natural fit via backend tool + REST/HTTP bridges |
| Voice control of smart speakers | Cross | Domain | New | — |
| Scene/routine triggers | Cross | Domain | New | Home Assistant / IFTTT-style |

## 6. Productivity & Knowledge (LLM-powered)

| Capability | Platform | Source | Feasibility | Notes |
|---|---|---|---|---|
| Free-form Q&A / reasoning | Backend | Repo-arch | Now | Any of 4 LLM providers; system prompt routes to tools |
| Web search grounding | Backend | Domain | New | Add a `web_search` tool (e.g. via provider or SerpAPI) |
| Summarize / extract from text | Backend | Repo-arch | Now | Pure LLM capability, already reachable |
| Multi-provider failover | Backend | Repo-arch | Now | `ProviderRouter` + `CircuitBreaker` + `retry_policy` real |
| Live provider/model switch (no restart) | Backend | Repo-arch | Now | `POST /api/v1/providers/select`; Android Providers screen lists them |

## 7. Memory & Personalization

| Capability | Platform | Source | Feasibility | Notes |
|---|---|---|---|---|
| Conversation history persistence | Both | Repo-arch | Now | SQLite locally, Postgres/Supabase in cloud (`persistent_store`) |
| User preferences store | Backend | Repo-arch | Now | `user_preferences` table + `MemoryStore.setPreference` |
| Cross-device memory sync | Backend | Repo-arch | Now | Supabase/Postgres path already implemented |
| Personalized routines | Both | Domain | New | Learn from history; not modeled |

## 8. Security & Privacy

| Capability | Platform | Source | Feasibility | Notes |
|---|---|---|---|---|
| Single-use confirmation tokens | Backend | Repo-arch | Now | `token_manager` (secrets.token_urlsafe(32), 300s TTL, replay-protected) |
| API-key log redaction | Backend | Repo-arch | Now | `redaction.py` masks Groq/OpenRouter/Gemini key patterns |
| WebSocket auth token | Backend | Repo-arch | Now | `validate_ws_token` gate on `/ws` |
| On-device processing (privacy) | Android | Repo-arch | Now | Local-first design; fast path never leaves device |
| End-to-end encryption of transcripts | Cross | Domain | New | Not in repo |

## 9. Multi-modal

| Capability | Platform | Source | Feasibility | Notes |
|---|---|---|---|---|
| Camera → vision LLM (describe scene) | Both | Domain | New | Pipe `CameraController` frame to a vision model (Gemini supports it) |
| Screen → vision LLM (understand UI) | Both | Domain | New | Strong upgrade over text-only `readScreen` |
| Image generation | Backend | Domain | New | Add image-gen tool |

## 10. Cross-Platform & Cloud

| Capability | Platform | Source | Feasibility | Notes |
|---|---|---|---|---|
| Cloud brain (render/Docker/systemd) | Backend | Repo-arch | Now | `render.yaml`, `Dockerfile`, systemd unit documented |
| Android ↔ cloud realtime (WebSocket) | Both | Repo-arch | Near | WS protocol defined (`schema.json`); Android `WebSocketClient` is stub |
| Emulator / LAN / cloud connection matrix | Both | Repo-arch | Now | Documented in `apk_connection.md` |
| Web dashboard / PWA | Cross | Domain | New | Not in repo |

## 11. Automation & Routines

| Capability | Platform | Source | Feasibility | Notes |
|---|---|---|---|---|
| Scheduled tasks / reminders | Both | Domain | New | Needs scheduler + notification |
| Location-based triggers | Android | Domain | New | Needs `FusedLocation` + geofence |
| "If this then that" rules | Both | Domain | New | Natural on top of tool registry |

## 12. Developer / Extensibility

| Capability | Platform | Source | Feasibility | Notes |
|---|---|---|---|---|
| Tool registry (add custom tools) | Backend | Repo-arch | Now | `ToolRegistry.register()` + auto LLM schemas |
| Plugin / skill marketplace | Cross | Domain | New | Natural extension of tool registry |
| REST + WS API for 3rd parties | Backend | Repo-arch | Now | `/api/v1/tools`, `/ws` exist |
| Unit + integration test suites | Both | Repo-arch | Now | 6 backend pytest + 4 Android unit tests present |

---

## Cross-cutting recommendations (from deep-read)

1. **Close the device-action gap first.** The biggest capability blocker is that
   Android device controllers and the backend `ToolExecutor` are stubs. Wiring
   real `SystemController`/`MediaController`/`CallController`/`SmsController` to
   Android APIs (and accessibility for tap/type) unlocks ~70% of the command
   surface with no new architecture.
2. **Plumb real STT/TTS/VAD.** `VoiceRuntime` is ready; drop in Vosk/Whisper
   (STT), Android `TextToSpeech` (TTS), and a simple energy VAD.
3. **Promote `whatsapp_send` to Android.** Backend has it; Android `JarvisIntent`
   does not yet — add the intent + intent-resolver branch + accessibility/intent
   send.
4. **Stream tokens over WS.** `stream()` exists on providers; expose via
   `action_result` chunks for a live "Jarvis is thinking" UX.
5. **Add a `web_search` tool** to the registry for grounded answers (cheap, high
   impact, Domain-standard).

See `02_IMPLEMENTED_NOW.md` for exactly what is real today, and
`03_OPTIONAL_FUTURE.md` for the optional backlog.
