# JARVIS / AND9 — Project Charter & Governance

## 1. Executive Purpose
Transform the JARVIS/AND9 assistant into a reliable, low-latency, maintainable, production-grade Android AI assistant with truthful device action verification, zero unhandled stalls, and deterministic voice pipelines.

## 2. Core Operational Rule
$$\text{DISPATCHED} \neq \text{EXECUTED} \neq \text{VERIFIED} = \text{COMPLETED}$$

- Under no circumstance may the system declare "Task complete" without verifiable telemetry or device state proof.
- Partial failures must be truthfully reported to the user via TTS.

## 3. Performance SLA & Latency Budgets
| Metric | Threshold Target | Hard Ceiling |
|---|---|---|
| Wake-word Accept → STT Ready | <= 150 ms | 400 ms |
| Speech End → Final STT Transcript | <= 800 ms | 1500 ms |
| Deterministic Local Command | <= 300 ms | 1000 ms |
| Cloud Reasoning / Complex Plan | <= 1500 ms | 4000 ms |
| Multi-Action Execution (per step) | <= 800 ms | 2000 ms |
| Absolute Processing Watchdog | — | 6000 ms |
| Idle Background CPU | <= 3% | 5% |

## 4. Governance & Module Authority
| Subsystem | Canonical Authority (Android) | Canonical Authority (Backend) |
|---|---|---|
| Voice Lifecycle | `VoiceRuntime` + `VoiceStateMachine` | — |
| Audio Sessions | `AudioSessionManager` + `MicController` | — |
| Intent Planning | `LocalTaskPlanner` + `IntentResolver` | `TaskPlanner` (`planner.py`) |
| Action Execution | `ActionExecutor` + `TaskExecutionCoordinator` | `ExecutionOrchestrator` (`execution_orchestrator.py`) |
| Network & Health | `BackendHealthManager` + `ApiClient` | `FastAPI Health Routes` (`/health/live`, `/health/ready`) |
| Realtime Device Protocol | `ConnectionManager` | `CommandRegistry` (`command_registry.py`) |
