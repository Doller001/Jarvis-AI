# Jarvis — Optional / Future Backlog

> **Purpose:** Things NOT in the repo today and NOT strictly required for a
> working v1, but worth considering. Ordered by impact-vs-effort. Items marked
> **(cheap, high-impact)** are recommended to pick up right after the must-fix
> gaps in `02_IMPLEMENTED_NOW.md`.
>
> Source: domain best-practice for an Android-first AI assistant (not repo-sourced
> unless noted `Repo-arch`).

---

## Tier 1 — Cheap, High-Impact (do next)

| Idea | Why | Effort | Depends on |
|---|---|---|---|
| **Implement `ProviderManager.kt`** (Repo-arch) | Currently empty → blocks compile; just delegate to a registry of the 4 providers | Low | None (must-fix) |
| **`web_search` tool** | Grounded answers; standard for assistants; add to `tools/registry.py` + a provider call | Low | Backend LLM gateway |
| **Stream tokens over WS** | Live "thinking" UX; `LLMProvider.stream()` already exists | Low–Med | Real `WebSocketClient` (Android) |
| **WhatsApp intent on Android** (Repo-arch) | Backend already has `whatsapp_send`; Android `JarvisIntent` lacks it | Low | IntentResolver + send path |
| **Real STT/TTS via Android APIs** | `VoiceRuntime` ready; drop in `TextToSpeech` + SpeechRecognizer/Whisper | Med | None |
| **Notification read/shown** | `NotificationController` stub → add `NotificationListenerService` | Med | Accessibility/perms |

## Tier 2 — High-Value Features

| Idea | Why | Effort |
|---|---|---|
| **On-device LLM (Ollama/MediaPipe)** | Full offline mode; privacy; latency | Med–High |
| **Vision: camera/screen → multimodal LLM** | Describe what the camera/sees; Gemini supports vision | Med |
| **Routines & scheduled tasks** | "Remind me…", "at 8am…"; needs scheduler + notifications | Med |
| **Location-based automations** | Geofenced triggers; `FusedLocation` + geofence | Med |
| **Smart-home bridge** (Hue/Home Assistant) | Big Surface; backend tool + REST/HTTP | Med |
| **Barge-in conversation** | Interrupt mid-response like Gemini Live | High |
| **Email & calendar integration** | Read/compose; standard assistant surface | Med |
| **Music control** (Spotify/etc.) | Common voice command | Med |
| **Cross-device memory + web dashboard/PWA** | Beyond phone; manage from browser | High |

## Tier 3 — Stretch / Experimental

| Idea | Why | Effort |
|---|---|---|
| **Plugin / skill marketplace** | Extend tool registry into user-installable skills | High |
| **End-to-end encrypted transcripts** | Privacy hardening | Med |
| **Wear OS companion** | Watch control surface | High |
| **iOS companion app** | Cross-ecosystem | Very High |
| **On-device wake-word model** (Sherpa-ONNX / KWS) | Real hotword ML with ONNX runtime mobile | Done |
| **Personalized adaptive routines** | Learn from history | High |
| **Image generation tool** | Creative surface | Low–Med |
| **Localization / i18n** (Repo-arch: Hinglish already partially supported) | Multi-language UI + commands | Med |
| **Telemetry / anonymized analytics** | Improve model routing & failures | Low |

---

## Suggested sequencing

1. **Unblock compile** → implement `ProviderManager.kt`, fill device-controller
   stubs with real Android calls, replace `WebSocketClient`/`ApiClient` stubs with
   real implementations. (Closes the biggest gaps; see `02_IMPLEMENTED_NOW.md`.)
2. **Make it actually work end-to-end** → real STT/TTS + accessibility tap/type +
   backend `ToolExecutor` either performs server-side or reliably delegates to the
   device. Then APK compiles and commands truly execute.
3. **Tier 1 optional** → `web_search`, token streaming, WhatsApp, notification read.
4. **Tier 2/3** → vision, routines, smart home, companions, plugins.

Cross-reference: `01_FEATURES_DEEPSEARCH.md` (full capability map),
`02_IMPLEMENTED_NOW.md` (current as-built inventory).
