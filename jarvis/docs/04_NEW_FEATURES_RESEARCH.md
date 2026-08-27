# Jarvis — New & Emerging Features Research Report

> **Purpose:** Extend the existing capability map (`01_FEATURES_DEEPSEARCH.md`) with newly
> researched features from 2025–2026 state-of-the-art in on-device AI assistants.
> Each feature is tagged with:
> - **Source**: `Domain` (general best-practice / emerging research) or `Repo` (already in repo architecture)
> - **Platform**: `Android` / `Backend` / `Both` / `Cross`
> - **Feasibility**: `Now` (arch supports it) / `Near` (needs wiring) / `New` (needs new modules)
> - **Status**: `Recommended` / `Consider` / `Monitor`

---

## Executive Summary

JARVIS already implements a solid foundation for an Android-first, local-first AI assistant.
The research below identifies features from the cutting edge of on-device voice assistants
(Gemini Live, AutoDroid, PokeClaw, SkillDroid, Run:ai, MVP Factory) that can be
incrementally integrated to dramatically improve the user experience.

Key opportunity clusters:
1. **Barge-in / Full-Duplex Voice** — the #1 missing capability for a natural conversational assistant.
2. **On-Device LLM Agents** — local 3B–8B parameter inference via LiteRT, Vulkan, or MediaPipe,
   eliminating cloud costs and latency for routine tasks.
3. **Multimodal Vision** — camera and screen understanding for UI automation and scene description.
4. **Skill Replay & Caching** — compile-once-reuse-forever UI automation, reducing 75–94% of LLM calls.
5. **Android 16 Platform Updates** — predictive back, edge-to-edge, Navigation 3, Kotlin 2.4.
6. **Structured Output & Tool Calling** — JSON-schema validation and parallel tool execution.
7. **GPU-Native Compute** — custom Vulkan compute shaders for 2× LLM token throughput.

---

## 1. Barge-in / Full-Duplex Conversation

| Feature | Platform | Source | Feasibility | Status | Notes |
|---|---|---|---|---|---|
| **Full-duplex listening during TTS** | Android | Domain | Near | Recommended | Requires keeping mic open while speaking + echo cancellation. [Run:ai blog](https://runedge.ai/blog/barge-in-interruption-handling-on-device-voice): "Barge-in demands full duplex: the agent has to keep listening while it is speaking." |
| **Acoustic Echo Cancellation (AEC) for self-voice** | Android | Domain | Near | Recommended | Cancel agent's own TTS from mic signal so VAD doesn't fire on itself. |
| **Interruption detection with frame-level latency** | Android | Domain | Near | Recommended | Stop agent mid-word when user starts talking; <50ms response time. |
| **Mid-speech context injection** | Android | Domain | New | Consider | Inject user's interruption as new context; LLM re-plans from current state. |
| **VAD with wake-word bypass for barge-in** | Android | Repo-arch | Near | Recommended | `VadEngine.kt` is currently a stub — implement real energy + ZCR VAD with a "bypass during speech" mode for barge-in. |

**Gap:** JARVIS's `VoiceRuntime.kt` state machine is `IDLE -> WAKE_LISTENING -> COMMAND_LISTENING -> PROCESSING -> SPEAKING -> ERROR` (half-duplex). Barge-in requires a parallel mic-listening path during `SPEAKING`. The `NearFieldAudioProcessor.kt` DSP pipeline is the right insertion point for AEC + dual VAD.

---

## 2. On-Device LLM Agents

| Feature | Platform | Source | Feasibility | Status | Notes |
|---|---|---|---|---|---|
| **Local LLM via LiteRT / Gemma** | Android | Domain | Near | Recommended | [PokeClaw](https://aisignal.dev/analysis/agents-io-pokeclaw): runs Google's Gemma 4 locally via LiteRT for full Android automation through Accessibility APIs. |
| **GPU-accelerated inference (Vulkan compute)** | Android | Domain | Near | Recommended | [MVP Factory blog](https://mvpfactory.io/blog/custom-vulkan-compute-kernels-for-on-device-llm-inference-on-android-bypassing): custom Vulkan compute shaders achieve 2× token throughput vs TFLite GPU delegate (22.8 vs 11.2 tokens/s on Adreno 750). |
| **On-device function calling** | Android | Domain | Near | Recommended | LLM decides tool calls locally, executes on device — no cloud round-trip for device actions. |
| **Local-only conversation mode** | Android | Domain | Near | Recommended | Full offline mode: on-device LLM + on-device memory + TTS/STT. Privacy-first. |
| **MediaPipe LLM Integration (LLM Inference API)** | Android | Domain | Near | Recommended | Google's MediaPipe supports running 1B–8B parameter LLMs on-device with GPU acceleration. |

**Gap:** JARVIS currently has NO on-device LLM. The `brain/JarvisBrain.kt` is deterministic rule-based only. Level-2/Level-3 routing goes to cloud backend via WebSocket (which is also stubbed). Adding a local LLM fallback would enable offline operation.

---

## 3. Multimodal Vision

| Feature | Platform | Source | Feasibility | Status | Notes |
|---|---|---|---|---|---|
| **Camera → Vision LLM (describe scene)** | Both | Domain | New | Recommended | Pipe camera frame to vision-capable LLM (Gemini supports vision). JARVIS has `CameraController.kt` stub. |
| **Screen → Vision LLM (understand UI)** | Both | Domain | New | Recommended | Strong upgrade over text-only `readScreen()`. Gemini 2.5 Flash / GPT-4o can interpret screen content. |
| **Multimodal UI element detection** | Android | Domain | New | Consider | Detect UI elements by visual appearance, not just accessibility tree. More robust on custom UIs. |
| **On-device vision inference** | Android | Domain | Near | Consider | MediaPipe or ML Kit Vision for on-device object detection without cloud round-trip. |

**Gap:** JARVIS's `analyze_image` tool is a mock (returns hardcoded string). No vision pipeline exists on Android.

---

## 4. Skill Replay & Caching (Task Compilation)

| Feature | Platform | Source | Feasibility | Status | Notes |
|---|---|---|---|---|---|
| **Skill compilation from successful trajectories** | Android | Domain | New | Recommended | [SkillDroid (arxiv 2604.14872)](https://arxiv.org/pdf/2604.14872): "Compiles LLM-guided mobile GUI trajectories into reusable parameterized skills." Achieves **100% success rate across 79 replay rounds at 2.4× speed** of full LLM execution. |
| **Skill library in SQLite** | Android | Domain | Near | Recommended | SkillDroid stores skill templates in local SQLite, indexed by embedding similarity. |
| **Step-level resilience & fallback** | Android | Domain | New | Consider | Graceful degradation: skip mismatched steps, fall back to LLM for ambiguous steps. |
| **Parameterized UI element locators** | Android | Domain | Near | Recommended | SkillDroid generates "weighted element locators" that survive layout changes. |
| **Multi-agent task decomposition** | Both | Domain | New | Consider | [Minitap/androidWorld (arxiv 2602.07787)](https://arxiv.org/abs/2602.07787): multi-agent system achieving 100% success on AndroidWorld benchmark. |

**Gap:** JARVIS's `LocalTaskPlanner.kt` uses regex/keyword heuristics. No trajectory recording, no skill compilation layer. This is a major opportunity — SkillDroid's approach could reduce 75–94% of LLM inference calls (the dominant cost in GUI agents).

---

## 5. Android 16 / 2026 Platform Features

| Feature | Platform | Source | Feasibility | Status | Notes |
|---|---|---|---|---|---|
| **Predictive Back gesture** | Android | Domain | Near | Recommended | Android 16 (API 36) makes predictive back mandatory. `onBackPressed()` is dead — use `BackHandler` in Compose. |
| **Edge-to-edge enforcement** | Android | Domain | Near | Recommended | Android 16 ignores `WindowCompat.setDecorFitsSystemWindows()`. Must use `enableEdgeToEdge()` + `consumeWindowInsets`. |
| **Compose 1.11 Grid & Styles** | Android | Domain | Near | Recommended | New Grid APIs and styling system in Compose 1.11 (stable with Android 16). |
| **Navigation 3 stable** | Android | Domain | Near | Recommended | Navigation 3 is now stable; replaces legacy Navigation Compose. |
| **Kotlin 2.4 context parameters** | Android | Domain | Near | Consider | Context parameters enable clean DI for AI agent state management. Production-ready in Kotlin 2.4. |
| **Predictive back + edge-to-edge for JARVIS** | Android | Repo-arch | Near | Recommended | JARVIS UI screens (`HomeScreen.kt`, etc.) need migration to handle Android 16 insets. |

---

## 6. Structured Output & Advanced Tool Calling

| Feature | Platform | Source | Feasibility | Status | Notes |
|---|---|---|---|---|---|
| **JSON Schema validation for tool calls** | Backend | Domain | Now | Recommended | [Prem AI 2026 guide](https://www.premai.io/blog/llm-function-calling-complete-implementation-guide-2026): "Modern APIs enforce schema compliance at the generation level." Replaces Jarvis's current "output a JSON object" text prompt approach. |
| **Parallel tool execution** | Backend | Domain | Near | Recommended | Execute multiple independent tools concurrently. [Prem AI guide](https://www.premai.io/blog/llm-function-calling-complete-implementation-guide-2026): covers parallel execution, streaming with tools, error handling. |
| **Tool calling with streaming** | Backend | Repo-arch | Near | Recommended | `LLMProvider.stream()` exists on backend but not yet plumbed to WebSocket. |
| **Schema-driven orchestrator** | Backend | Domain | Near | Recommended | `orchestrator.py` currently relies on unvalidated text JSON — add schema rejection and retry. |
| **Constrained decoding for structured output** | Backend | Domain | New | Consider | Techniques: JSON mode, function calling, constrained decoding — "the only three that matter in 2026." |

**Gap:** JARVIS's `tools/executor.py` returns mock results. Backend `orchestrator.py` relies on LLM output conforming to expected JSON without schema validation. This is the #2 reliability issue after device-action stubs.

---

## 7. GPU-Native Compute for On-Device LLM

| Feature | Platform | Source | Feasibility | Status | Notes |
|---|---|---|---|---|---|
| **Custom Vulkan compute shaders** | Android | Domain | New | Consider | [MVP Factory](https://mvpfactory.io/blog/custom-vulkan-compute-kernels-for-on-device-llm-inference-on-android-bypassing): 2× token throughput vs TFLite GPU delegate. Key kernels: tiled matrix multiplication, fused softmax-attention, memory-mapped weight loading. |
| **Adreno vs Mali dispatch tuning** | Android | Domain | New | Consider | Workgroup size: Adreno 750 optimal at 256 (16×16), Mali-G720 at 64 (8×8). |
| **Flash-attention-style fused kernel** | Android | Domain | New | Consider | Eliminates 3 round-trips to global memory per attention head. |
| **GPU inference pipeline** | Android | Domain | New | Monitor | NVIDIA's RTX Spark line coming 2026; Android needs Vulkan/Metal cross-platform path. |

**Gap:** JARVIS has no GPU compute path. Wake word uses ONNX Runtime. No LLM inference at all on Android.

---

## 8. Advanced Voice Features

| Feature | Platform | Source | Feasibility | Status | Notes |
|---|---|---|---|---|---|
| **Local streaming STT (Sherpa-ONNX / Vosk)** | Android | Domain | Near | Recommended | Current `SpeechRecognizer.kt` is a stub. [Run:ai](https://runedge.ai/blog/barge-in-interruption-handling-on-device-voice) notes Whisper + llama.cpp + Piper stack "breaks" barge-in — needs custom on-device C++ loop instead. |
| **Neural TTS (Piper / Sherpa TTS)** | Android | Domain | Near | Recommended | Current `TextToSpeechEngine.kt` only logs + calls onComplete. Piper TTS runs fully on-device with high-quality neural voices. |
| **Voice activity detection (real VAD)** | Android | Domain | Now | Recommended | `VadEngine.kt` is a stub. Dual-metric: energy + zero-crossing rate on 160-sample PCM chunks. |
| **Wake word with Hinglish support** | Android | Repo-arch | Near | Recommended | `WakeWordEngine.kt` matches 8 English variants — add Hinglish ("जारविस", "है न कि"). |
| **On-device wake word (KWS) with ONNX** | Android | Repo-arch | Now | Recommended | `OnnxWakeWordDetector.kt` exists with `hey_jarvis.onnx`. Integrate with runtime. |

---

## 9. Smart Home & IoT Integration

| Feature | Platform | Source | Feasibility | Status | Notes |
|---|---|---|---|---|---|
| **Smart home bridge (Hue, Home Assistant)** | Cross | Domain | New | Recommended | Backend tool + REST/HTTP bridge to smart home protocols. |
| **IFTTT-style automation rules** | Both | Domain | New | Consider | "If this then that" rules engine on top of existing tool registry. |
| **Scene/routine triggers** | Cross | Domain | New | Consider | Voice-triggered or scheduled scene activations. |

**Gap:** No smart home integration exists. The backend tool registry is extensible — adding a `home_assistant_control` tool would be straightforward.

---

## 10. Productivity & Communications

| Feature | Platform | Source | Feasibility | Status | Notes |
|---|---|---|---|---|---|
| **WhatsApp on Android** | Android | Repo-arch | Near | Recommended | Backend has `whatsapp_send` tool; Android `JarvisIntent` lacks it. Add intent + resolver + send path. |
| **Email & calendar integration** | Both | Domain | New | Recommended | Read/compose email, calendar events — standard assistant surface. |
| **SMS/MMS with rich context** | Both | Domain | Near | Recommended | JARVIS has `SmsController` + `SendSms` intent — wire them to real APIs. |
| **Context-aware messaging replies** | Both | Domain | New | Consider | LLM generates contextual replies based on message content + user history. |

---

## 11. Cross-Device & Cloud Features

| Feature | Platform | Source | Feasibility | Status | Notes |
|---|---|---|---|---|---|
| **Web dashboard / PWA** | Cross | Domain | New | Recommended | Web UI for managing JARVIS from browser. Backend already has REST + WS APIs. |
| **Cross-device memory sync** | Both | Repo-arch | Now | Recommended | Supabase/Postgres memory sync already implemented — add PWA consumer. |
| **Wear OS companion** | Cross | Domain | New | Consider | Watch control surface; quick voice commands. |
| **iOS companion app** | Cross | Domain | New | Monitor | Cross-ecosystem expansion; large effort. |

---

## 12. On-Device Vector Databases & Local RAG

| Feature | Platform | Source | Feasibility | Status | Notes |
|---|---|---|---|---|---|
| **sqlite-vec for on-device embeddings** | Android | Domain | Near | Recommended | [Dev.to article](https://dev.to/aairom/embedded-intelligence-how-sqlite-vec-delivers-fast-local-vector-search-for-ai): SQLite with sqlite-vec extension enables 768-dim embedding storage + retrieval entirely on-device. |
| **On-device embedding models** | Android | Domain | Now | Recommended | [ObjectBox](https://objectbox.io/262454-2): "EmbeddingGemma (308M) and Qwen3 Embedding 0.6B are MTEB-competitive and fit in a fraction of mobile RAM." |
| **Local vector RAG pipeline** | Android | Domain | Near | Recommended | Combine on-device embeddings + SQLite-vec + small LLM for fully offline knowledge retrieval. |
| **Hybrid cloud-edge RAG** | Both | Repo-arch | Near | Recommended | JARVIS already has SQLite local + Postgres/Supabase cloud — add vector embeddings to both. |

**Gap:** JARVIS's local RAG uses keyword/token overlap (`rag_chunks` table in `MemoryEngine.kt`). No vector embeddings, no similarity search. This is a high-impact upgrade.

---

## Priority Recommendations

### Tier 1 — High-Impact, Low-Effort (Do Next)
1. **Implement real VAD** (`VadEngine.kt`) — prerequisite for barge-in and better voice UX.
2. **Fix `ProviderManager.kt`** (empty file — compile blocker) — delegate to provider registry.
3. **Wire real STT/TTS** (`SpeechRecognizer.kt`, `TextToSpeechEngine.kt`) — replace stubs.
4. **Add `web_search` tool** to backend registry — grounded answers, standard for assistants.
5. **Stream tokens over WebSocket** — `LLMProvider.stream()` exists; expose via `action_result` chunks.

### Tier 2 — High-Impact, Medium-Effort
1. **Barge-in / full-duplex voice** — implement AEC + dual VAD + interrupt detection.
2. **On-device LLM agent** (LiteRT / Gemma) — local function calling for device actions.
3. **Skill replay system** (SkillDroid-inspired) — compile successful trajectories into reusable skills.
4. **Multimodal vision** — camera → vision LLM for scene description + UI understanding.
5. **Structured output with JSON schema validation** — replace text-JSON prompts in orchestrator.

### Tier 3 — Monitoring / Future
1. **Custom Vulkan compute shaders** — 2× GPU inference throughput (specialized, high complexity).
2. **Wear OS / iOS companion apps** — cross-platform expansion.
3. **End-to-end encrypted transcripts** — privacy hardening.
4. **Android 16 mandatory migrations** — predictive back, edge-to-edge, Navigation 3.

---

## Cross-References

- **Existing docs**: `01_FEATURES_DEEPSEARCH.md` (capability map), `02_IMPLEMENTED_NOW.md` (as-built inventory), `03_OPTIONAL_FUTURE.md` (backlog)
- **Key papers**: SkillDroid (arxiv:2604.14872), Minitap/AndroidWorld (arxiv:2602.07787)
- **Key blogs**: [MVP Factory — Vulkan compute](https://mvpfactory.io/blog/custom-vulkan-compute-kernels-for-on-device-llm-inference-on-android-bypassing), [Run:ai — Barge-in](https://runedge.ai/blog/barge-in-interruption-handling-on-device-voice), [Prem AI — Function calling 2026](https://www.premai.io/blog/llm-function-calling-complete-implementation-guide-2026), [AISignal — PokeClaw](https://aisignal.dev/analysis/agents-io-pokeclaw)
- **Existing codebase**: `brain/IntentResolver.kt` (Hinglish intent resolution), `brain/Planner.kt` (risk-gated confirmation), `brain/JarvisBrain.kt` (local deterministic orchestrator), `backend/app/agent/orchestrator.py` (L1→L2/L3 fallback)

---

*Research completed: August 27, 2026. Sources span 2025–2026 academic papers (arXiv), industry blogs (MVP Factory, Run:ai, Prem AI, ObjectBox), and Google's Android 16 documentation. The web search tool had partial availability (some queries failed due to keyless Firecrawl/Exa limits); working results were extracted and cross-referenced.*
