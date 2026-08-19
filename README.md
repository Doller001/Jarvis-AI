# Raphael AI Assistant

> Local-first Android AI assistant with modular architecture, fail-closed safety gates, and multi-provider LLM gateway.

## Architecture

Raphael is a voice-first AI assistant for Android that executes device commands locally when safe and routes complex requests through a FastAPI backend with WebSocket realtime communication.

### Key Design Principles
- **Local-first:** Deterministic commands execute on-device without network latency
- **Fail-closed safety:** Unknown/risky actions require explicit user confirmation
- **Modular architecture:** Domain-specific matchers, executors, and pipeline stages
- **Multi-provider resilience:** Circuit breaker with automatic LLM provider failover

## Project Structure

```
├── docs/                    # Project documentation (13 files)
├── phase1/                  # Intent Parser & Execution Engine (25 files)
│   ├── android/brain/       # CommandParser + 7 DomainMatchers
│   ├── android/execution/   # LocalCommandDispatcher + 7 DomainExecutors
│   └── backend/             # ToolRegistry (41 tools, fail-closed gate)
├── phase2/                  # Realtime WebSocket & Wire Protocol (10 files)
│   ├── android/core/network/# WireModels, ApiClient, WebSocketClient
│   └── backend/app/realtime/# protocol, connection_manager, message_router
├── phase3/                  # Modular Agent Brain (12 files)
│   └── backend/app/agent/   # intent/ → reasoning/ → planning/ → policy/ → execution/
├── phase4/                  # Glassmorphism UI & Setup Wizard (4 files)
│   └── android/ui/          # RaphaelGlassHome, PermissionManager, ViewModel
├── phase5/                  # Accessibility Service (4 files)
│   └── android/accessibility/ # ScreenNodeInspector, GestureDispatcher, UIAutomator
├── phase6/                  # RAG & Memory Subsystems (7 files)
│   └── backend/app/         # memory/ (conversation, profile) + rag/ (vector search)
├── phase7/                  # Multi-Provider LLM Gateway (5 files)
│   └── backend/app/llm/     # CircuitBreaker, ProviderRouter, RetryPolicy, Gateway
├── phase8/                  # Services Refactoring (6 files)
│   └── backend/app/services/# music/ (indexer, resolver) + ui/ (layout definitions)
├── phase9/                  # Deployment & Docker (4 files)
│   └── backend/             # Dockerfile, render.yaml, app/main.py
└── phase10/                 # E2E WebSocket Simulation (7 files)
    └── backend/             # Full 8-step WS handshake test suite
```

## Tech Stack

| Layer | Technology |
|---|---|
| Android | Kotlin + Jetpack Compose |
| Voice | Sherpa-ONNX (KWS/VAD/STT/TTS) |
| Backend | Python + FastAPI |
| Realtime | WebSockets |
| Database | PostgreSQL + SQLAlchemy + Alembic |
| LLM | Groq (primary) → OpenRouter (fallback) |
| Deployment | Render (Docker) |

## Test Results

```
66 / 66 backend pytest cases PASSED (100%)
```

| Suite | Tests |
|---|---:|
| Tool Registry risk tiers & safety gate | 51 |
| Agent Brain pipeline | 3 |
| RAG & Memory vector search | 5 |
| LLM Gateway circuit breaker | 3 |
| Music & UI services | 3 |
| Health check & deployment | 3 |
| E2E WebSocket simulation | 1 |

## Safety Model

- **`RiskTier.SAFE`** → Auto-execute (e.g., get_time, torch_on)
- **`RiskTier.CONFIRMATION_REQUIRED`** → User confirmation required (e.g., call, WhatsApp)
- **Unknown tools** → Fail-closed to `CONFIRMATION_REQUIRED`
- **Password fields** → Automatically masked in accessibility screen reader

## License

All rights reserved © Saif Ali
