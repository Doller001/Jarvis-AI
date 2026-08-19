# Jarvis AI — Voice Subsystem & Wake-Word Configuration

## 1. Wake Phrase Variants

The local `WakeWordEngine` recognizes the following wake phrase patterns:
- `Jarvis`
- `Hey Jarvis`
- `Hay Jarvis`
- `Hey, Jarvis`
- `Jarvis hello`
- `Jarvis suno`
- `Jarvis listen`
- `Jarvis listen to me`

## 2. Resource-Aware Lifecycle

```text
IDLE (Wake word only)
        ↓ (phrase match)
WAKE_DETECTED (Activate VAD)
        ↓
LISTENING (STT active)
        ↓
PROCESSING (Local Intent / LLM)
        ↓
SPEAKING (TTS output)
        ↓
IDLE (Return to wake-word mode)
```
