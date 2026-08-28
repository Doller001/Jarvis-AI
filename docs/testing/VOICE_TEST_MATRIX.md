# JARVIS AI — VOICE TEST MATRIX & INSTRUMENTATION SCENARIOS

> **Target:** Verification matrix for all 10 Instrumentation Scenarios (Part 51)  

---

| Test ID | Scenario Description | Initial State | Trigger Action | Expected State Transitions | Success Criteria |
|---|---|---|---|---|---|
| **TEST-A** | Cold Application Startup | Not Running | App Launch | `DISABLED -> WAKE_LISTENING` | `SpeechRecognizer` is NEVER started; `MicController` owned by `WAKE_WORD` only |
| **TEST-B** | Idle Silence (60s) | `WAKE_LISTENING` | Ambient Silence | `WAKE_LISTENING -> WAKE_LISTENING` | `COMMAND_LISTENING` is NEVER entered |
| **TEST-C** | Spoken Wake Word | `WAKE_LISTENING` | User speaks "Hey Jarvis" | `WAKE_LISTENING -> ACKNOWLEDGING -> COMMAND_LISTENING` | Wake AudioRecord cleanly released before STT start |
| **TEST-D** | Manual Mic Button (Wake Disabled) | `DISABLED` | User taps mic icon in UI | `DISABLED -> COMMAND_LISTENING` | `VoiceEvent.ManualCommandStart` accepted; STT starts |
| **TEST-E** | Cloud Access Token Expiry (15m) | `ONLINE` | Send command with expired token | `Chat -> 401 -> Refresh -> Retry` | Command completes successfully; new tokens saved |
| **TEST-F** | Stale / Invalid Refresh Token | `ONLINE` | Token refresh fails (401) | `Refresh Fail -> Re-Register -> Retry` | Device re-registered; command succeeds without user intervention |
| **TEST-G** | Render Service Cold-Start (502/503) | `CONNECTING` | Backend booting | `BACKEND_STARTING -> Bounded Backoff -> ONLINE` | App does NOT switch to permanent offline mode |
| **TEST-H** | Physical Network Disconnection | `OFFLINE` | No Wi-Fi / Cellular | Local Fast-Path Execution | Local commands (Torch, Volume, App launch) continue to work |
| **TEST-I** | Wake Engine Hardware Error | `WAKE_LISTENING` | Microphone disconnect / error | `WAKE_LISTENING -> RECOVERING -> WAKE_LISTENING` | Mic ownership cleared; no deadlock or ANR |
| **TEST-J** | Rapid Repeated Mic Taps | `DISABLED` | 5 rapid taps in 500ms | Single `COMMAND_LISTENING` session | Session generation guard prevents duplicate STT sessions |
