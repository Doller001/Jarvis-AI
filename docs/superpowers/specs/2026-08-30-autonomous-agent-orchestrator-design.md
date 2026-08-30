# Autonomous Agent Orchestrator Architecture Specification

- **Date**: 2026-08-30
- **Status**: Approved / In Review
- **Author**: Antigravity & Minaty
- **Topic**: Autonomous Agent Orchestration with Tri-Tier Multimodal Memory and Closed-Loop Tool Execution

---

## 1. Overview & Objectives

The **Autonomous Agent Orchestrator** connects a reasoning brain (`JarvisBrain`), a multi-tiered multimodal memory system (`CAGCache`, `RAGEngine`, `MAGStore`), and programmable system tools (`ToolExecutor` & Device Execution Gateway) into a deterministic, closed-loop execution lifecycle.

### Core Objectives:
1. **Deterministic Latency**: Sub-10ms query responses for frequent/identical intents using Context-Aware Generation (CAG) fast caching.
2. **Episodic & Semantic Recall**: Hybrid SQLite FTS5 and semantic embedding search (RAG) for episodic conversation transcripts and knowledge snippets.
3. **Structured Entity & Hardware State**: Long-term Memory-Augmented Graph (MAG) for user preferences, entity relations, and device capability constraints.
4. **Multimodal Sensory Ingestion**: Seamless ingestion of sensory telemetry (battery, network, volume, sensor flags) and vision/image frames.
5. **Closed-Loop Tool Verification & Dynamic Self-Correction**: Observation feedback loop that verifies tool execution and applies automated fallback policies when hardware or software tools fail.

---

## 2. System Architecture

```
       ┌────────────────────────────────────────────────────────┐
       │                  MULTIMODAL SENSORS                    │
       │     (Audio / Vision / Network / Device Telemetry)      │
       └──────────────────────────┬─────────────────────────────┘
                                  │ Sensory Ingestion (REST / WS)
                                  ▼
 ┌─────────────────────────────────────────────────────────────────────┐
 │                       MULTIMODAL MEMORY                             │
 │  ┌─────────────────────┐ ┌───────────────────┐ ┌──────────────────┐ │
 │  │ CAG (Context Cache) │ │ RAG (Vector/Doc)  │ │ MAG (Facts/Pref) │ │
 │  │ SHA-256 Fast Cache  │ │ Hybrid FTS5+Embed │ │ Long-Term Graph  │ │
 │  └─────────────────────┘ └───────────────────┘ └──────────────────┘ │
 └────────────────────────────────┬────────────────────────────────────┘
                                  │ Context-Augmented Prompt
                                  ▼
 ┌─────────────────────────────────────────────────────────────────────┐
 │                       ORCHESTRATION BRAIN                           │
 │     [Perception] ➔ [Plan / Decompose] ➔ [Select Tool & Verify]      │
 └──────────────────────────┬──────────────────────────────────────────┘
                            │ Structured Action Dispatch
                            ▼
 ┌─────────────────────────────────────────────────────────────────────┐
 │                    PROGRAMMABLE SYSTEM TOOLS                        │
 │  ┌───────────────────────┐ ┌──────────────────────────────────────┐ │
 │  │   Software Services   │ │        Hardware & OS Native          │ │
 │  │ (Search, Media, APIs) │ │ (Shell, Torch, Audio, WS Device)     │ │
 │  └───────────────────────┘ └──────────────────────────────────────┘ │
 └──────────────────────────┬──────────────────────────────────────────┘
                            │ Observation / Feedback Loop
                            └───────────────┘
```

---

## 3. Subsystem Specifications

### 3.1 Tri-Tier Memory Engine (`app/memory/`)

1. **Context-Aware Generator (CAG) - `cag_cache.py`**
   - **Mechanism**: In-memory SHA-256 hash lookup keyed on normalized intent + relevant sensory state fingerprint.
   - **TTL & Invalidation**: Configurable TTL (default: 300s for dynamic queries, persistent for static facts).
   - **Performance Target**: `< 5ms` hit latency, zero LLM / disk I/O.
   - **Interface**:
     ```python
     class CAGCache:
         def get(self, intent_hash: str) -> Optional[dict[str, Any]]: ...
         def set(self, intent_hash: str, response: dict[str, Any], ttl_seconds: int = 300) -> None: ...
         def invalidate(self, pattern: Optional[str] = None) -> None: ...
     ```

2. **Retrieval-Augmented Generation (RAG) - `rag_engine.py`**
   - **Mechanism**: Hybrid SQLite FTS5 full-text indexing + local cosine similarity on message/document chunks.
   - **Episodic Memory**: Automatic chunking and indexing of past conversation messages and system summaries.
   - **Knowledge Retrieval**: Searches episodic logs and knowledge docs; formats top-K relevant chunks within strict token budgets (max 500 tokens).
   - **Interface**:
     ```python
     class RAGEngine:
         def index_chunk(self, session_id: str, content: str, metadata: dict[str, Any]) -> str: ...
         def search(self, query: str, session_id: Optional[str] = None, top_k: int = 3) -> list[dict[str, Any]]: ...
     ```

3. **Memory-Augmented Graph (MAG) - `mag_store.py`**
   - **Mechanism**: Key-value and entity-relation persistence built atop SQLite / Supabase / MongoDB.
   - **Stored Entities**: User profile, preferences, device capabilities (e.g., `bluetooth_supported`, `camera_available`), and hardware state constraints.
   - **Interface**:
     ```python
     class MAGStore:
         def get_fact(self, key: str) -> Optional[Any]: ...
         def set_fact(self, key: str, value: Any, category: str = "general") -> None: ...
         def get_hardware_profile(self, device_id: str) -> dict[str, Any]: ...
     ```

4. **Multimodal Memory Facade - `multimodal_memory.py`**
   - Coordinates CAG, RAG, and MAG into a single unified context provider for `JarvisBrain`.
   - Method `retrieve_context(query: str, session_id: str, sensory: Optional[dict[str, Any]]) -> dict[str, Any]`.

---

### 3.2 Multimodal Sensory Ingestion

- **Payload Model (`app/agent/execution_models.py`)**:
  ```python
  class SensoryTelemetry(BaseModel):
      battery_level: Optional[int] = None
      is_charging: Optional[bool] = None
      network_type: Optional[str] = None # wifi, cellular, offline
      volume_level: Optional[int] = None
      current_audio_output: Optional[str] = None
      extra_sensors: dict[str, Any] = Field(default_factory=dict)

  class MultimodalInputPayload(BaseModel):
      text: str
      session_id: str = "default-session"
      request_id: str = Field(default_factory=lambda: f"req-{uuid.uuid4().hex[:8]}")
      sensory_data: Optional[SensoryTelemetry] = None
      image_base64: Optional[str] = None
      image_uri: Optional[str] = None
  ```

---

### 3.3 Closed-Loop Tool Execution & Recovery Policy

1. **Observation & Verification**:
   - Every tool dispatch returns an execution outcome.
   - For connected device tools, WebSocket ACKs and verified state events confirm physical or software state change.
2. **Dynamic Recovery & Fallback**:
   - When tool execution fails (e.g. timeout, missing permission, network error):
     - Check MAG hardware profile for alternative tools (e.g. toggle mobile data when wifi toggle fails; fall back to local synthesis when cloud TTS times out).
     - If no alternative exists, apply safe default recovery (revert to last known safe state) and synthesize clear diagnostic feedback to the user.
3. **Safety Circuit Breakers**:
   - Maximum 3 retries with exponential backoff.
   - Strict Pydantic parameter validation before dispatching to shell or device runtime.

---

## 4. API & Integration Surface

- `POST /chat` and WebSocket `/ws/chat`:
  - Updated to accept `MultimodalInputPayload`.
  - Check CAG cache immediately: if found, return cached response with header/attribute `cached: true` and latency `< 5ms`.
  - Assemble RAG + MAG context into the prompt when invoking LLM Gateway.
  - Return execution report including verified steps and recovery actions (if any).

---

## 5. Testing Strategy

1. **Unit Tests**:
   - `test_cag_cache.py`: Cache hits, miss fallthrough, TTL expiration, invalidation.
   - `test_rag_engine.py`: SQLite FTS5 search, semantic chunk ranking, token boundary enforcement.
   - `test_mag_store.py`: Structured fact storage, hardware profile resolution, persistence across sessions.
   - `test_multimodal_memory.py`: Tri-tier context assembly and sensory enrichment.
2. **Integration Tests**:
   - `test_autonomous_orchestrator.py`: Full end-to-end flow with sensory inputs, tool execution, failed tool dynamic recovery, and closed-loop verification.
   - Full regression suite execution (`pytest`).

---

## 6. Constraints & Invariants

- Zero breaking changes to existing endpoints (`/chat`, `/api/v1/chat/completions`, WebSocket protocols).
- Fallback gracefully to SQLite if external databases (Supabase/Postgres/Mongo) are unreachable.
- No heavy third-party embedding models required for default local execution (pure Python / SQLite FTS5 + cosine baseline with optional provider embeddings).
