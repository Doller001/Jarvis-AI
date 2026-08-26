# JARVIS / AND9 — Canonical Module Ownership

## 1. Subsystem Architecture Map

```text
android/
  ├── voice/               <- Single authority: VoiceRuntime, VoiceStateMachine, AudioSessionManager, MicController
  ├── brain/               <- Single intent resolver: IntentResolver, CommandParser
  ├── execution/           <- Single coordinator: TaskExecutionCoordinator
  ├── actionengine/        <- Single executor: ActionExecutor, LocalTaskPlanner
  ├── device/              <- Hardware controllers: AppController, SystemController, MediaController
  ├── network/             <- Networking: BackendHealthManager, ApiClient, ConnectionManager
  ├── telemetry/           <- Diagnostics: DiagnosticEventBus, VoiceDiagnostics
  └── ui/                  <- Jetpack Compose Views & JarvisViewModel

backend/
  ├── app/agent/           <- Multi-action planner: planner.py, execution_orchestrator.py, execution_models.py
  ├── app/realtime/        <- Command state machine: command_registry.py, message_router.py, protocol.py
  ├── app/api/             <- REST API & Health checks: routes.py, main.py
  ├── app/llm/             <- Provider fallback & retry: gateway.py, retry_policy.py
  └── app/security/        <- WebSocket auth & verification: auth.py
```

## 2. Ownership Directives
- **No Parallel Executors**: All device actions must funnel through `TaskExecutionCoordinator` on Android and `ExecutionOrchestrator` on backend.
- **Single Mic Authority**: `MicController` is the single source of truth for audio capture exclusivity (`OWNER_WAKE` vs `OWNER_STT`).
