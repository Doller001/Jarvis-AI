# JARVIS AI — ROOT CAUSE ANALYSIS

> **Document Version:** 1.0.0  
> **Target:** Root Cause Analysis of 16 Known Failure Modes  

---

## Analysis of the 16 Problem Areas

### 1. "Jarvis Cloud authentication expired" appears frequently
- **Root Cause**: Access tokens have a 15-minute TTL. In `ApiClient.kt`, `sendChat()` checks `authTokenManager.isTokenExpired(token)`. When `true`, it immediately short-circuits with `"Backend authentication is not ready"` without attempting a token refresh. The ViewModel translates this into an expired auth UI banner.
- **Resolution**: Implement proactive token refresh (when `< 2 minutes` remaining) and an automated HTTP 401 interceptor that refreshes the token and transparently retries the request once.

### 2. Cloud authentication becomes unavailable even when the user is online
- **Root Cause**: When a token refresh request failed or returned a 401, `ApiClient.refreshAccessToken()` called `authTokenManager.clearTokens()`. Clearing the tokens deleted the stored device credentials from local storage. Without automated re-registration, the app remained permanently unauthenticated until killed and restarted.
- **Resolution**: Add fallback device re-registration when refresh fails, recovering credentials seamlessly.

### 3. The app spends too much time in offline/degraded behavior
- **Root Cause**: In `BackendHealthManager.kt`, transient Render cold starts (HTTP 502/503 for ~15s) or individual provider discovery failures were classified as `HealthStatus.OFFLINE`, forcing `JarvisViewModel` into local offline fallback.
- **Resolution**: Introduce distinct states: `BACKEND_STARTING`, `DEGRADED`, and `OFFLINE`. Bounded exponential backoff retries for cold starts; decouple health endpoints from LLM provider discovery.

### 4. Voice listening can start automatically without the user saying the wake word
- **Root Cause**: `VoiceRuntime.startListeningForCommand()` was exposed as a public zero-argument method without any caller verification. Background lifecycle events (such as service restarts or reconnect triggers) could invoke it without a confirmed wake-word event.
- **Resolution**: Create a strict `CommandListeningRequest(reason: TriggerReason, sessionId: String)` gate where only `WakeWordConfirmed`, `ManualButton`, and `BargeInInterrupt` are accepted.

### 5. Manual wake/listening activation sometimes does not work
- **Root Cause**: `VoiceStateMachine` initialized in `VoiceState.DISABLED` when wake word was toggled off. The state transition map strictly allowed `DISABLED -> WAKE_LISTENING`. When the user tapped the manual mic button, the requested transition `DISABLED -> COMMAND_LISTENING` was logged as `ILLEGAL` and aborted.
- **Resolution**: Add `VoiceEvent.ManualCommandStart` allowing legal transition from `DISABLED -> COMMAND_LISTENING`.

### 6. Wake word is detected inconsistently
- **Root Cause**: AudioRecord buffer allocation was variable, and the audio pipeline lacked a dynamic noise floor calibration gate, causing ambient background noise in outdoor environments to swamp the ONNX mel-spectrogram features.
- **Resolution**: Standardize ONNX input chunking, enforce noise-floor calibration in `NearFieldAudioProcessor`, and maintain fixed buffer pools.

### 7. Wake-word → command-listening handoff is unreliable
- **Root Cause**: In `LiveKitWakeWordEngine.pause()`, thread join was capped at 50ms. If `AudioRecord.read()` was blocking in the native audio driver, the thread remained alive while `SpeechController` attempted to open a new recording session, causing native `AudioRecord` contention (`E/AudioRecord: start() status -38`).
- **Resolution**: Synchronous, verified release of `AudioRecord` before `MicController` issues `OWNER_STT`.

### 8. Microphone ownership can become inconsistent
- **Root Cause**: `SpeechController` used `micController.forceAcquire()`, overriding any active owner without checking if the previous capture thread was fully terminated.
- **Resolution**: Replace `forceAcquire()` with atomic state machine synchronization.

### 9. Backend calls sometimes fail after authentication state changes
- **Root Cause**: When tokens were refreshed, active WebSocket connections and in-flight HTTP requests used stale authorization headers.
- **Resolution**: Centralize token distribution in `AuthRepository` with an event bus notifying network clients upon token renewal.

### 10. Refresh-token behavior is incomplete
- **Root Cause**: Backend `JWTManager.refresh_access_token()` minted a new access token but never rotated the refresh token, and did not track revocations in a persistent store.
- **Resolution**: Implement refresh token rotation, storing SHA-256 hashes in `auth_sessions` with token reuse detection.

### 11–13. Duplicate Render deployment configs and Dockerfiles
- **Root Cause**: Historical branches left `/render.yaml`, `/jarvis/render.yaml`, `/jarvis/backend/render.yaml`, `/Dockerfile`, and `/jarvis/backend/Dockerfile`.
- **Resolution**: Enforce single canonical `jarvis/render.yaml` deploying `jarvis/backend/Dockerfile` with `dockerContext: backend`. Delete stale files.

### 14. Documentation contains stale backend URLs
- **Root Cause**: Legacy URLs such as `and9-1.onrender.com` remained in documentation.
- **Resolution**: Standardize all documentation on `https://jarvis-ai-59qd.onrender.com`.

### 15. Cloud/offline provider configuration is misleading
- **Root Cause**: `OLLAMA_BASE_URL` defaulted to `http://localhost:11434`, causing continuous unreachable warnings in cloud environments where Ollama is not installed.
- **Resolution**: Set `OLLAMA_ENABLED=false` by default on Render cloud.

### 16. Overlapping responsibilities and duplicated logic
- **Root Cause**: Health checking, token refreshing, and registration logic were duplicated across `JarvisViewModel`, `BackendHealthManager`, and `ApiClient`.
- **Resolution**: Centralize into `AuthRepository`, `BackendRepository`, and `BackendConnectionManager`.
