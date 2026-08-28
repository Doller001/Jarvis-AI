# JARVIS AI — VOICE & ACOUSTIC ENGINE ARCHITECTURE

> **Subsystem:** On-Device Speech Pipeline, State Machine & Microphone Ownership  

---

## 1. Canonical Voice State Machine

```mermaid
stateDiagram-v2
    [*] --> DISABLED
    
    DISABLED --> WAKE_LISTENING : Enable Wake Monitoring
    DISABLED --> COMMAND_LISTENING : Manual Mic Button (ManualCommandStart)
    
    WAKE_LISTENING --> ACKNOWLEDGING : Wake Word Confirmed ("Hey Jarvis")
    WAKE_LISTENING --> COMMAND_LISTENING : Manual Mic Button
    WAKE_LISTENING --> DISABLED : Disable Wake Monitoring
    
    ACKNOWLEDGING --> COMMAND_LISTENING : Wake Capture Released
    ACKNOWLEDGING --> RECOVERING : Handoff Error
    
    COMMAND_LISTENING --> PROCESSING : Speech Recognized (onResults)
    COMMAND_LISTENING --> RECOVERING : Timeout (8s) / STT Error
    
    PROCESSING --> SPEAKING : TTS Response Ready
    PROCESSING --> INTERRUPTING : Barge-In Detected
    PROCESSING --> RECOVERING : Processing Timeout (6s)
    
    SPEAKING --> WAKE_LISTENING : TTS Completed (Wake Enabled)
    SPEAKING --> DISABLED : TTS Completed (Wake Disabled)
    SPEAKING --> INTERRUPTING : User Interrupts Assistant
    
    INTERRUPTING --> COMMAND_LISTENING : TTS Stopped & Mic Acquired
    
    RECOVERING --> WAKE_LISTENING : Reset Done (Wake Enabled)
    RECOVERING --> DISABLED : Reset Done (Wake Disabled)
```

---

## 2. Atomic Microphone Ownership Matrix

| Ownership State | Active Component | Recording Hardware | Audio Source | Restrictions |
|---|---|---|---|---|
| `NONE` | None | Released (`AudioRecord == null`) | — | Microphone completely free |
| `WAKE_WORD` | `LiveKitWakeWordEngine` | `AudioRecord` (16kHz Mono 16-bit PCM) | `VOICE_RECOGNITION` | Continuous ONNX inference; must release before STT |
| `COMMAND_STT` | `SpeechController` | Native Android `SpeechRecognizer` | Platform Managed | Exclusively owns audio session until utterance complete |
| `INTERRUPT` | `InterruptDetector` | Lightweight RMS Audio Stream | `VOICE_RECOGNITION` | Active ONLY during `SPEAKING` state |

---

## 3. Strict Wake → Command Handoff Sequence

```mermaid
sequenceDiagram
    autonumber
    participant Wake as LiveKitWakeWordEngine
    participant VR as VoiceRuntime
    participant Mic as MicController
    participant STT as SpeechController
    participant User

    User->>Wake: "Hey Jarvis"
    Wake->>Wake: ONNX KWS Match (score >= threshold)
    Wake->>VR: onWakeDetected()
    VR->>VR: Transition to ACKNOWLEDGING
    VR->>Wake: pauseSync()
    Wake->>Wake: audioRecord.stop() & release()
    Wake->>Wake: captureThread.join() (verified termination)
    Wake->>Mic: releaseMic(OWNER_WAKE)
    VR->>Mic: acquireMic(OWNER_STT)
    VR->>VR: Transition to COMMAND_LISTENING
    VR->>STT: startListening(reason=WakeWordConfirmed, sessionGeneration)
    User->>STT: "Open YouTube"
    STT->>VR: onResults("Open YouTube")
    VR->>STT: destroy()
    VR->>Mic: releaseMic(OWNER_STT)
    VR->>VR: Transition to PROCESSING
```
