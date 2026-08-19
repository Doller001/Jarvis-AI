# Jarvis AI — System Architecture

## 1. Local-First Android Architecture

Jarvis is designed as an **Android-first local AI assistant**. Simple commands are executed deterministically on-device without network latency or cloud LLM calls.

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

## 2. Low-Latency Voice Subsystem (`com.jarvis.assistant.voice`)

- **Wake Word Engine**: Listens locally for "Jarvis", "Hey Jarvis", "Hay Jarvis", "Jarvis suno", etc.
- **Resource-Aware Activation**: Keeps heavy STT/TTS unmapped during IDLE state to conserve CPU/battery.
- **Continuous Flow**: Automatically transitions to VAD → STT → Execution → TTS without requiring button taps.

---

## 3. Phone Control & Accessibility Layer

- **JarvisAccessibilityService**: Performs UI tree inspection with password masking.
- **AccessibilityController**: Provides safe high-level APIs (`tap`, `scroll`, `back`, `home`, `openRecents`, `typeText`, `readScreen`).
