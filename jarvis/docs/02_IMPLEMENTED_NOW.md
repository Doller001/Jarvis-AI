# Jarvis — What Is Actually Implemented Now

> **Scope:** Current state of the working tree at
> `/home/saif/Downloads/raphael-ai-assistant-main/jarvis`.
> Each item is marked **REAL** (does real work) or **STUB** (logs / returns
> hardcoded data / no real side-effect). This is the honest "as-built" inventory.
>
> Built/verified in the prior session: the **Compose UI rebuild** (4 screens),
> **real PermissionManager**, **launcher-icon build fix**, and **XML validation**
> (9/9 resources well-formed). The **real APK compile could not finish in-session**
> (Android toolchain hosts throttled to KB/s); `scripts/build_and_verify.sh` ships
> the real build for a normal-network machine.

---

## A. Android Client (`android/app/src/main/kotlin/com/jarvis/assistant`)

### UI (rebuilt — Compose, REAL, compile-ready)
| File | Status | Notes |
|---|---|---|
| `ui/MainActivity.kt` | REAL | `NavHost` (onboarding→home→conversation→providers) + `JarvisViewModel` |
| `ui/JarvisViewModel.kt` | REAL | `StateFlow<JarvisUiState>`; aggregates PermissionManager, ConnectionManager, ProviderManager, MemoryStore, JarvisBrain |
| `ui/theme/Color.kt`, `Theme.kt` | REAL | `JarvisBlue` palette + `JarvisTheme` |
| `ui/components/JarvisComponents.kt` | REAL | Reusable Compose components (244 lines) |
| `ui/screens/OnboardingScreen.kt` | REAL | Lists all perms; **Grant** buttons trigger real request/settings flows; gates "Continue" on `allRequiredGranted` |
| `ui/screens/HomeScreen.kt` | REAL | Listening orb, connection pill, provider/model, memory |
| `ui/screens/ConversationScreen.kt` | REAL | Chat bubbles bound to `MemoryStore`; type/mic input |
| `ui/screens/ProvidersScreen.kt` | REAL | Groq/OpenRouter/Gemini/Ollama cards; active select |
| `ui/permissions/PermissionModels.kt` | REAL | Permission catalog + `GrantKind` (runtime vs settings) |

### Permissions (REAL — was a stub returning `true`)
| File | Status | Notes |
|---|---|---|
| `permissions/PermissionManager.kt` | REAL | `ContextCompat.checkSelfPermission` (mic/camera/call/contacts/sms), `Settings.Secure` accessibility check, `PowerManager` battery-optimization check, `POST_NOTIFICATIONS` TIRAMISU guard. `PermissionState.allRequiredGranted` + `grantedCount` |

### Voice
| File | Status | Notes |
|---|---|---|
| `voice/WakeWordEngine.kt` | REAL | Phrase matching for 8 variants + 1.5s cooldown suppression |
| `voice/VoiceRuntime.kt` | REAL (wiring) | Full state machine IDLE→WAKE_DETECTED→LISTENING→PROCESSING→SPEAKING; delegates to leaves |
| `voice/VadEngine.kt` | STUB | `activate()/deactivate()` only set a flag + log |
| `voice/SpeechRecognizer.kt` | STUB | `startListening` logs only; never returns text |
| `voice/TextToSpeechEngine.kt` | STUB | `speak()` logs + immediately calls `onComplete` |
| `voice/AudioManager.kt` | STUB | Log-only routing |

### Brain (on-device deterministic)
| File | Status | Notes |
|---|---|---|
| `brain/JarvisBrain.kt` | REAL | Orchestrates parser→planner→response |
| `brain/CommandParser.kt` | REAL | Delegates to `IntentResolver` |
| `brain/IntentResolver.kt` | REAL | `sealed class JarvisIntent` (11 intents) + keyword resolve **with Hinglish** (e.g. "torch chalo", "volume badhao", "screen padho") |
| `brain/Planner.kt` | REAL | `ExecutionPlan`; call/SMS require confirmation |
| `brain/ResponseGenerator.kt` | REAL | Spoken responses per intent |
| `llm/ProviderManager.kt` | **EMPTY FILE** | No implementation; `JarvisViewModel` calls `selectProviderAndModel` — **will not compile until filled** (see Known Gaps) |

### Accessibility
| File | Status | Notes |
|---|---|---|
| `accessibility/JarvisAccessibilityService.kt` | REAL (skeleton) | Receives events; calls `ScreenInspector` + `AccessibilityController` |
| `accessibility/AccessibilityController.kt` | MIXED | `back()/home()/openRecents()` use **real** `GLOBAL_ACTION_*`; `tap/tapById/scroll/typeText/readScreen` are **STUBS** (log + return true/string) |
| `accessibility/ScreenInspector.kt` | present (not deep-read) | Node-tree inspection |

### Device Controllers (ALL STUBS — log-only, no real action)
`device/SystemController.kt` (wifi/bt/torch/volume), `device/MediaController.kt`
(play/pause + CameraController + NotificationController + ContactsController +
CallController + SmsController). None perform real device operations yet.

### Network (STUBS — no real HTTP/WS)
| File | Status | Notes |
|---|---|---|
| `network/ApiClient.kt` | STUB | Returns hardcoded `["Groq","OpenRouter","Gemini","Ollama"]`; base URL `and9-1.onrender.com` (pre-existing edit) |
| `network/WebSocketClient.kt` | STUB | Logs connect; `ConnectionManager` only flips a state field |
| `network/ConnectionManager.kt` | REAL (state) | `ConnectionState` enum + setter; no socket |

### App shell (REAL)
`app/JarvisApplication.kt` (RuntimeState enum + init),
`services/JarvisForegroundService.kt` (notification channel + startForeground +
voiceRuntime), `services/BootRecoveryReceiver` (declared in manifest).

### Resources (added — build fix)
`res/mipmap-anydpi-v26/ic_launcher(_round).xml` + `res/drawable/ic_launcher_*`
(adaptive icon — **fixes the missing-`@mipmap` build blocker**),
`res/values/colors.xml`, `strings.xml`. `build.gradle.kts` updated
(compileSdk 34, navigation-compose, lifecycle-viewmodel/runtime-compose,
material-icons-extended).

### Android tests (REAL)
`IntentResolverTest.kt`, `TaskManagerTest.kt`, `WakeWordEngineTest.kt`,
`permissions/PermissionStateTest.kt` (pure-JVM aggregation test).

---

## B. Backend (`backend/app`)

| Module | Status | Notes |
|---|---|---|
| `main.py` | REAL | FastAPI app, CORS, correlation-id middleware, `/health`, `/api/v1/health`, exception handlers, routers |
| `realtime/ws.py` | REAL | WS endpoint; `validate_ws_token` gate; `session_ready`; routes to `message_router` |
| `realtime/message_router.py` | REAL | command / confirmation / ping handling; token consume on confirm |
| `realtime/connection_manager.py` | REAL | Per-session socket registry |
| `realtime/protocol.py` + `shared/protocol/schema.json` | REAL | 8 event types (connect/command/confirmation/confirmation_request/action_result/error/ping/pong) |
| `agent/orchestrator.py` | REAL | L1 deterministic → L2/L3 LLM fallback; risk gate → token or execute |
| `agent/intent_resolver.py` | REAL | Deterministic resolver **with Hinglish**; risky actions flagged |
| `agent/normalizer.py` | REAL | Lowercase/whitespace normalize |
| `agent/planner.py` | REAL | `RISKY_ACTIONS` = {call_contact, whatsapp_send, send_sms, delete_file, install_apk, change_security_settings} |
| `tools/registry.py` | REAL | 11 tools (8 safe + 3 risky: call_contact/send_sms/whatsapp_send); auto LLM schemas |
| `tools/executor.py` | **STUB** | Returns `{"status":"success",...}` mock — does NOT perform real device action (by design device action is on-device; backend mock is still a stub) |
| `llm/registry.py` | REAL | 4 providers, discovery (hides unauthenticated), active selection |
| `llm/gateway.py` | REAL | Failover chain + local-rule fallback |
| `llm/router.py` | REAL | `CircuitBreaker` per provider |
| `llm/retry_policy.py`, `llm/circuit_breaker.py` | REAL | Present + used |
| `llm/providers/groq.py` | **REAL HTTP** | Calls `api.groq.com`; validate_key/list_models/generate real |
| `llm/providers/openrouter.py` | **REAL HTTP** | Calls `openrouter.ai/api/v1` |
| `llm/providers/gemini.py` | **REAL HTTP** | Calls `generativelanguage.googleapis.com` |
| `llm/providers/ollama.py` | **REAL HTTP** | Calls local `:11434/api` |
| `memory/memory_manager.py` | REAL | Facade over persistent store |
| `memory/persistent_store.py` | **REAL** | SQLite + Postgres/Supabase (psycopg2) with auto-fallback |
| `security/token_manager.py` | **REAL** | Single-use tokens, 300s TTL, replay protection |
| `security/redaction.py` | **REAL** | Masks Groq/OpenRouter/Gemini key patterns in logs |
| `security/auth.py` | REAL | `validate_ws_token`, `get_allowed_origins` |
| `api/routes.py` | REAL | `GET /api/v1/tools` |
| `api/providers_api.py` | REAL | Provider discovery + `POST /api/v1/providers/select` |

### Backend tests (REAL)
`test_health.py`, `test_jarvis_brain.py`, `test_llm_providers.py`,
`test_security.py`, `test_tools.py`, `test_websocket.py` (pytest + pytest-asyncio).

---

## C. Docs & Scripts (REAL)
- **Docs (11):** `architecture.md`, `providers.md`, `apk_connection.md`,
  `setup.md`, `deployment.md`, `supabase.md`, `security.md`, `voice.md`,
  `accessibility.md`, `UI_REBUILD_PLAN.md`, plus this set.
- **Scripts:** `build_and_verify.sh` (real build), `setup_build_env.sh`
  (abandoned — toolchain too slow), `validate_xml.py` (9/9 XML passed),
  `run_backend.sh`, `test_all.sh`.

---

## Known Gaps / Hard Truths (must-fix before a working build)
1. **`llm/ProviderManager.kt` is an EMPTY FILE** on Android, but `JarvisViewModel`
   calls `providerManager.selectProviderAndModel(...)`. This is a **compile error**
   as-is. Either implement it (delegate to `ProviderRegistry`) or remove the call.
2. **Android device controllers are stubs** → commands "succeed" but do nothing
   on the phone.
3. **Backend `ToolExecutor` is a mock** → device actions never actually happen
   server-side (correct only if the Android client performs them; but the Android
   client also stubs them). Net: nothing real executes today.
4. **Network layer is stubbed both ends** → Android never reaches the backend
   (no real `WebSocketClient`/HTTP), so the LLM gateway is unreachable from the app.
5. **Real APK not compiled in-session** (toolchain throttle). Use
   `scripts/build_and_verify.sh` on a normal network.

See `01_FEATURES_DEEPSEARCH.md` for the full capability map and
`03_OPTIONAL_FUTURE.md` for the optional backlog.
