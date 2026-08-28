# JARVIS AI — COMPLETE ARCHITECTURE AUDIT

> **Audit Date:** 2026-08-28  
> **Auditors:** Senior Android Audio Architect, Kotlin Systems Engineer, FastAPI Backend Architect, Security Engineer, Production Reliability Engineer  
> **Target Production URL:** `https://jarvis-ai-59qd.onrender.com`  
> **Scope:** Full-stack codebase audit across Android Client, Python FastAPI Backend, ONNX Wake-Word Pipeline, Security & Token Lifecycle, Render Deployment, and Storage Engines.

---

## 1. Executive Summary & Inventory

JARVIS AI is a dual-tier cognitive assistant: a low-latency native Android client running local-first edge speech processing and hardware automation, coupled with a cloud FastAPI backend providing multi-provider LLM orchestration, Supabase PostgreSQL persistent memory, and single-use action verification.

### Codebase Inventory

| Subsystem | Primary Files / Classes | Responsibilities | Current Audit Status |
|---|---|---|---|
| **Android Voice Engine** | `VoiceRuntime.kt`, `VoiceStateMachine.kt`, `SpeechController.kt`, `MicController.kt`, `NearFieldAudioProcessor.kt`, `AudioCapture.kt` | Manages acoustic lifecycle, state transitions, and audio hardware | **FRAGILE** — Illegal transitions between `DISABLED` and `COMMAND_LISTENING`, unverified mic theft via `forceAcquire`, lack of explicit trigger reasons |
| **Android Wake Word** | `LiveKitWakeWordEngine.kt`, `OnnxWakeWordDetector.kt`, `WakeWordConfig.kt` | 100% on-device continuous keyword spotting ("Hey Jarvis") | **AT RISK** — Race condition on `pause()` thread join (50ms timeout), AudioRecord state leakage during wake→STT handoff |
| **Android Networking & Auth** | `ApiClient.kt`, `AuthTokenManager.kt`, `BackendHealthManager.kt`, `WebSocketClient.kt` | Network communication, JWT storage, health monitoring | **DEFECTIVE** — Immediate failure on token expiration, no automated 401 retry loop, race conditions between registration & health check |
| **Android UI & ViewModel** | `JarvisViewModel.kt`, `MainActivity.kt`, `HomeScreen.kt`, `SettingsScreen.kt`, `ProvidersScreen.kt` | StateFlow UI orchestration, settings management | **LEAKY** — Static callbacks in `JarvisForegroundService`, viewmodel directly orchestrating network device registration |
| **Backend API Gateway** | `main.py`, `routes.py`, `openai_compat.py`, `providers_api.py`, `auth_routes.py` | REST API, OpenAI compatibility, provider discovery | **SOLIDIFIED** — Fixed 401 on provider discovery, added OpenAI completions & universal aliases |
| **Backend Security & Auth** | `auth.py`, `jwt_manager.py`, `device_registry.py`, `token_manager.py` | JWT creation/validation, HMAC token minting, device tracking | **VULNERABLE** — Device registry persisted to ephemeral container JSON file (`device_registry.json`), no refresh token session hashing or reuse detection |
| **Backend Storage** | `supabase_client.py`, `persistent_store.py`, `memory_manager.py` | PostgreSQL persistence, local WAL fallback | **PARTIAL** — Supabase client functional, but device registry does not leverage PostgreSQL |
| **Deployment Configs** | `render.yaml` (root, jarvis/, backend/), `Dockerfile` (root, backend/) | Render deployment blueprints and Docker containerization | **DUPLICATED** — Multiple competing `render.yaml` and `Dockerfile`s with differing CORS/ports |

---

## 2. Dependency Graph & Subsystem Boundaries

```mermaid
graph TD
    subgraph Android Client
        UI["Compose UI (HomeScreen / SettingsScreen)"]
        VM["JarvisViewModel"]
        FGS["JarvisForegroundService"]
        VR["VoiceRuntime (Single Voice Authority)"]
        SM["VoiceStateMachine"]
        MC["MicController (Single Mic Owner)"]
        WWE["LiveKitWakeWordEngine (ONNX Local)"]
        SC["SpeechController (Android STT)"]
        TTS["TextToSpeechEngine"]
        ATM["AuthTokenManager (EncryptedSharedPrefs)"]
        API["ApiClient (OkHttp Pool)"]
        WS["WebSocketClient"]
        BHM["BackendHealthManager"]
        COORD["TaskExecutionCoordinator"]
        ACT["ActionExecutor & Device Controllers"]
    end

    subgraph Cloud Backend (FastAPI on Render)
        MAIN["FastAPI Gateway (app.main)"]
        AUTH_R["AuthRouter (/api/v1/auth)"]
        OPENAI_R["OpenAIRouter (/v1/chat/completions)"]
        PROV_R["ProvidersRouter (/api/v1/providers)"]
        SYS_R["SystemRouter (/api/v1/health)"]
        BRAIN["JarvisBrain Orchestrator"]
        LLM_GW["LLMGateway (NVIDIA, Groq, OpenRouter, Gemini)"]
        DEV_REG["DeviceRegistry (PostgreSQL / In-Memory)"]
        JWT_M["JWTManager (HS256)"]
        SUPA["SupabaseClient (PostgreSQL Direct / REST)"]
    end

    UI --> VM
    FGS --> VR
    VM --> BHM
    VM --> API
    VM --> COORD
    VR --> SM
    VR --> MC
    VR --> WWE
    VR --> SC
    VR --> TTS
    COORD --> ACT

    BHM --> API
    BHM --> WS
    API --> MAIN
    WS --> MAIN

    MAIN --> AUTH_R
    MAIN --> OPENAI_R
    MAIN --> PROV_R
    MAIN --> SYS_R
    OPENAI_R --> BRAIN
    AUTH_R --> DEV_REG
    AUTH_R --> JWT_M
    BRAIN --> LLM_GW
    BRAIN --> SUPA
```

---

## 3. Data Flow & Lifecycles

### 3.1 Authentication Lifecycle (Current vs Required)
- **Current Flow**:
  1. App starts -> `JarvisViewModel` calls `ApiClient.registerDevice` asynchronously.
  2. `ApiClient` requests `/api/v1/auth/token` -> receives Access Token (15m TTL) and Refresh Token (30d TTL).
  3. `AuthTokenManager` stores tokens in `EncryptedSharedPreferences`.
  4. When 15 minutes pass, `ApiClient.sendChat` sees expired token and fails immediately with `"Backend authentication is not ready"`.
  5. If an HTTP 401 is received, no retry is performed. `refreshAccessToken` clears all tokens on failure, leaving the device in permanent auth failure until app restart.
- **Required Redesign**:
  1. Proactive Token Refresh: When access token remaining lifetime is `<= 2 minutes`, trigger background refresh before sending payload.
  2. Deterministic 401 Recovery Interceptor:
     `Request -> 401 -> Refresh Token -> Retry Request (1x) -> If Refresh Fails -> Re-Register Device -> Retry Request (1x) -> Final Error if still failing`.
  3. Server-side PostgreSQL Session Registry: Store SHA-256 hashed refresh tokens in `auth_sessions` table. Rotate refresh tokens on every refresh and detect reuse.

### 3.2 Voice & Wake-Word Lifecycle (Current vs Required)
- **Current Flow**:
  1. `LiveKitWakeWordEngine` records PCM frames via `AudioRecord` and runs ONNX inference.
  2. Upon wake word detection, `VoiceRuntime.handleWakeEvent()` transitions to `ACKNOWLEDGING` and calls `wakeEngine.pause()`.
  3. `pause()` attempts a 50ms join on the capture thread. If the thread is blocked in `AudioRecord.read()`, `SpeechController.startListening()` calls `micController.forceAcquire()`, resulting in two audio capture clients competing for the microphone.
  4. If the user disabled wake word in settings, the state is `DISABLED`. Tapping the manual mic icon attempts `DISABLED -> COMMAND_LISTENING`, which `VoiceStateMachine` rejects as an illegal transition.
- **Required Redesign**:
  1. Legal Transition: `DISABLED + ManualCommandStart -> COMMAND_LISTENING` and `WAKE_LISTENING + ManualCommandStart -> COMMAND_LISTENING`.
  2. Hard STT Guard: SpeechRecognizer start requires `CommandListeningRequest(reason, sessionId)`. Valid reasons: `WakeWordConfirmed`, `ManualButton`, `BargeInInterrupt`. Unknown reasons are rejected.
  3. Atomic Handoff: `LiveKitWakeWordEngine.pauseSync()` explicitly releases `AudioRecord` and waits for state `STATE_INITIALIZED` or thread death before `micController` permits `OWNER_STT` acquisition.
  4. Session Discard: Tag every voice cycle with a monotonic `VoiceSessionId`. Callbacks from older sessions are discarded.

---

## 4. Comprehensive Vulnerability & Reliability Matrix

| Issue ID | Subsystem | Defect Description | Severity | Remediation Strategy |
|---|---|---|---|---|
| **AUD-01** | Backend Deployment | 3 competing `render.yaml` files and 2 `Dockerfile`s with mismatched configs (`ALLOWED_ORIGINS=*`, port hardcodes) | **HIGH** | Delete root `render.yaml` and `backend/render.yaml`; retain only canonical `jarvis/render.yaml` deploying `jarvis/backend/Dockerfile` with `${PORT}`. |
| **AUD-02** | Backend Security | Server device registry uses ephemeral `device_registry.json`, causing device de-registration on Render container restart | **CRITICAL** | Migrate `DeviceRegistry` to Supabase PostgreSQL table `devices` with in-memory dev fallback. |
| **AUD-03** | Backend Security | Refresh tokens stored as plaintext JWTs without revocation tracking or hash verification | **HIGH** | Add `auth_sessions` PostgreSQL table storing SHA-256 hashed refresh tokens, session rotation, and reuse revocation. |
| **AUD-04** | Backend Security | `JARVIS_JWT_SECRET` falls back to default dev secret in production | **CRITICAL** | Add strict startup assertion failing application boot if `ENVIRONMENT=production` and JWT secret is missing or default. |
| **AUD-05** | Android Auth | `ApiClient.sendChat` aborts immediately when access token expires instead of refreshing | **HIGH** | Build `AuthInterceptor` with automated token refresh and single-retry device re-registration loop. |
| **AUD-06** | Android Auth | Generic "Jarvis Cloud authentication expired" error presented for all network failures | **MEDIUM** | Map HTTP status codes and exceptions to granular `DiagnosticState` (`AUTH_EXPIRED`, `BACKEND_COLD_START`, `NETWORK_OFFLINE`). |
| **AUD-07** | Android Voice | `VoiceStateMachine` forbids `DISABLED -> COMMAND_LISTENING`, breaking manual mic button when wake word is off | **HIGH** | Add `VoiceEvent.ManualCommandStart` with legal transitions from `DISABLED` and `WAKE_LISTENING`. |
| **AUD-08** | Android Voice | `SpeechController` uses `forceAcquire` without verifying wake `AudioRecord` teardown | **HIGH** | Require synchronous wake-engine mic release before granting `OWNER_STT` ownership. |
| **AUD-09** | Android Voice | SpeechRecognizer can start unexpectedly without verified trigger origin | **HIGH** | Implement `CommandListeningRequest` with mandatory `TriggerReason` validation. |
| **AUD-10** | Android Build | Release build falls back to debug signing if `KEYSTORE_PATH` is missing | **HIGH** | Enforce release build failure if production signing credentials are not supplied. |
| **AUD-11** | Backend Config | `OLLAMA_BASE_URL=http://localhost:11434` defaults to active on cloud Render, logging warnings on every boot | **LOW** | Add `OLLAMA_ENABLED=false` default flag in production render configuration. |
