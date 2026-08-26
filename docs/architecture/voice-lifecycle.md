# JARVIS / AND9 — Voice Pipeline Lifecycle

```text
               DISABLED
                  │ (setWakeEnabled=true)
                  ▼
            WAKE_LISTENING ◄─────────────────────────┐
                  │ (Wake Word Detected)             │
                  ▼                                  │
            ACKNOWLEDGING (Tone <= 80ms)             │
                  │                                  │
                  ▼                                  │
           COMMAND_LISTENING (STT Capturing)          │
                  │ (Final Result)                   │
                  ▼                                  │
              PROCESSING (Watchdog: 6000ms max)      │
                  │ (Plan + Execution)               │
                  ▼                                  │
               SPEAKING (TTS Spoken Response)        │
                  │                                  │
                  └──────────────────────────────────┘
```

## Watchdog & Fail-Safe Semantics
- If `PROCESSING` exceeds 6000ms, watchdog triggers `RECOVERING`, releases microphone, clears atomic session lock, and resumes `WAKE_LISTENING`.
