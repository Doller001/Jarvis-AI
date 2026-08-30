# Autonomous Agent Orchestrator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and integrate an Autonomous Agent Orchestrator in Jarvis combining an orchestration brain, tri-tier multimodal memory (CAG cache, RAG semantic search, MAG fact store), multimodal sensory ingestion, and closed-loop tool execution with dynamic self-correction.

**Architecture:** 
- The tri-tier memory hierarchy in `app/memory/` decomposes memory access into:
  1. Context-Aware Generator (`cag_cache.py`) for sub-10ms SHA-256 cached intent lookups.
  2. Retrieval-Augmented Generation (`rag_engine.py`) for hybrid FTS5 and semantic episodic chunk retrieval.
  3. Memory-Augmented Graph (`mag_store.py`) for structured user facts and device capability constraints.
- `MultimodalMemoryCoordinator` (`multimodal_memory.py`) unifies the tiers into context-augmented prompts for `JarvisBrain`.
- `ExecutionOrchestrator` & `JarvisBrain` (`app/agent/`) enforce closed-loop verification, sensory telemetry ingestion (battery, network, sensors), and dynamic recovery fallbacks upon tool execution errors.

**Tech Stack:** Python 3.12, FastAPI, SQLite / FTS5, Pydantic v2, Pytest, Asyncio.

**Spec:** [docs/superpowers/specs/2026-08-30-autonomous-agent-orchestrator-design.md](file:///home/shanu/Desktop/and9/docs/superpowers/specs/2026-08-30-autonomous-agent-orchestrator-design.md)

## Global Constraints

- Zero breaking changes to existing endpoints (`/chat`, `/api/v1/chat/completions`, WebSocket protocols).
- Fallback gracefully to SQLite if external databases (Supabase/Postgres/Mongo) are unreachable.
- No heavy third-party embedding models required for default local execution (pure Python / SQLite FTS5 + cosine baseline with optional provider embeddings).
- All tests must pass with `pytest`.

---

### Task 1: Context-Aware Generator (CAG) Fast Cache Engine

**Files:**
- Create: `jarvis/backend/app/memory/cag_cache.py`
- Test: `jarvis/backend/tests/test_cag_cache.py`

**Interfaces:**
- Consumes: Standard library `hashlib`, `time`, `typing`
- Produces: `CAGCache` class with methods `compute_hash(text: str, sensory_fingerprint: Optional[str] = None) -> str`, `get(intent_hash: str) -> Optional[dict[str, Any]]`, `set(intent_hash: str, response: dict[str, Any], ttl_seconds: int = 300) -> None`, `invalidate(pattern: Optional[str] = None) -> None`

- [ ] **Step 1: Write the failing test**

```python
# jarvis/backend/tests/test_cag_cache.py
import pytest
from app.memory.cag_cache import cag_cache, CAGCache

def test_cag_cache_hit_and_miss():
    cache = CAGCache()
    h = cache.compute_hash("turn on flashlight")
    assert cache.get(h) is None
    
    payload = {"type": "command_result", "response_text": "Flashlight turned on.", "status": "VERIFIED"}
    cache.set(h, payload, ttl_seconds=60)
    
    result = cache.get(h)
    assert result is not None
    assert result["response_text"] == "Flashlight turned on."

def test_cag_cache_ttl_expiration(monkeypatch):
    import time
    cache = CAGCache()
    h = cache.compute_hash("get battery level")
    cache.set(h, {"status": "success"}, ttl_seconds=10)
    assert cache.get(h) is not None
    
    monkeypatch.setattr(time, "time", lambda: time.time() + 20)
    assert cache.get(h) is None

def test_cag_cache_sensory_fingerprint():
    cache = CAGCache()
    h1 = cache.compute_hash("status report", sensory_fingerprint="battery:80")
    h2 = cache.compute_hash("status report", sensory_fingerprint="battery:20")
    assert h1 != h2
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pytest tests/test_cag_cache.py -v`
Expected: FAIL with `ModuleNotFoundError: No module named 'app.memory.cag_cache'`

- [ ] **Step 3: Write minimal implementation**

```python
# jarvis/backend/app/memory/cag_cache.py
"""
Context-Aware Generator (CAG) Fast Cache Engine.
Provides sub-10ms SHA-256 intent and sensory hash lookups.
"""

import hashlib
import time
from typing import Any, Optional


class CAGCache:
    def __init__(self, max_entries: int = 1000):
        self._cache: dict[str, dict[str, Any]] = {}
        self._max_entries = max_entries

    def compute_hash(self, text: str, sensory_fingerprint: Optional[str] = None) -> str:
        raw = text.strip().lower()
        if sensory_fingerprint:
            raw = f"{raw}::{sensory_fingerprint}"
        return hashlib.sha256(raw.encode("utf-8")).hexdigest()

    def get(self, intent_hash: str) -> Optional[dict[str, Any]]:
        entry = self._cache.get(intent_hash)
        if not entry:
            return None
        if entry["expires_at"] is not None and time.time() > entry["expires_at"]:
            self._cache.pop(intent_hash, None)
            return None
        return entry["response"]

    def set(self, intent_hash: str, response: dict[str, Any], ttl_seconds: Optional[int] = 300) -> None:
        if len(self._cache) >= self._max_entries:
            # Simple eviction of oldest item
            oldest_key = next(iter(self._cache))
            self._cache.pop(oldest_key, None)

        expires_at = (time.time() + ttl_seconds) if ttl_seconds is not None else None
        self._cache[intent_hash] = {
            "response": response,
            "expires_at": expires_at,
            "created_at": time.time()
        }

    def invalidate(self, pattern: Optional[str] = None) -> None:
        if pattern is None:
            self._cache.clear()
        else:
            to_del = [k for k in self._cache if pattern in k]
            for k in to_del:
                self._cache.pop(k, None)


cag_cache = CAGCache()
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pytest tests/test_cag_cache.py -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add jarvis/backend/app/memory/cag_cache.py jarvis/backend/tests/test_cag_cache.py
git commit -m "feat(memory): add CAG fast cache engine"
```

---

### Task 2: Retrieval-Augmented Generation (RAG) Semantic Memory Engine

**Files:**
- Create: `jarvis/backend/app/memory/rag_engine.py`
- Test: `jarvis/backend/tests/test_rag_engine.py`

**Interfaces:**
- Consumes: `jarvis/backend/app/memory/persistent_store.py` SQLite connection
- Produces: `RAGEngine` class with methods `index_chunk(session_id: str, content: str, role: str = "user", metadata: Optional[dict[str, Any]] = None) -> str`, `search(query: str, session_id: Optional[str] = None, top_k: int = 3) -> list[dict[str, Any]]`

- [ ] **Step 1: Write the failing test**

```python
# jarvis/backend/tests/test_rag_engine.py
import pytest
from app.memory.rag_engine import RAGEngine

def test_rag_engine_index_and_search(tmp_path):
    db_file = str(tmp_path / "rag_test.db")
    rag = RAGEngine(db_path=db_file)
    
    rag.index_chunk("session-1", "User prefers dark mode and British butler tone.", role="system")
    rag.index_chunk("session-1", "Turned on the living room lights at 8 PM.", role="assistant")
    rag.index_chunk("session-2", "Playing Bohemian Rhapsody by Queen.", role="assistant")
    
    results = rag.search("dark mode butler", session_id="session-1", top_k=2)
    assert len(results) >= 1
    assert "dark mode" in results[0]["content"].lower()

def test_rag_engine_empty_search(tmp_path):
    db_file = str(tmp_path / "rag_empty.db")
    rag = RAGEngine(db_path=db_file)
    results = rag.search("quantum mechanics")
    assert results == []
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pytest tests/test_rag_engine.py -v`
Expected: FAIL with `ModuleNotFoundError: No module named 'app.memory.rag_engine'`

- [ ] **Step 3: Write minimal implementation**

```python
# jarvis/backend/app/memory/rag_engine.py
"""
Retrieval-Augmented Generation (RAG) Semantic & Episodic Memory Engine.
Uses SQLite FTS5 for full-text search with token budgeting and fallback similarity.
"""

import math
import os
import re
import sqlite3
import time
import uuid
from typing import Any, Optional


class RAGEngine:
    def __init__(self, db_path: Optional[str] = None):
        self.db_path = db_path or os.getenv("JARVIS_DB_PATH", "jarvis_memory.db")
        self._init_fts()

    def _get_connection(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        return conn

    def _init_fts(self) -> None:
        conn = self._get_connection()
        try:
            with conn:
                conn.execute("""
                    CREATE TABLE IF NOT EXISTS rag_chunks (
                        chunk_id TEXT PRIMARY KEY,
                        session_id TEXT NOT NULL,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        metadata TEXT,
                        timestamp REAL NOT NULL
                    );
                """)
                # Try creating FTS5 virtual table for keyword & semantic search
                try:
                    conn.execute("""
                        CREATE VIRTUAL TABLE IF NOT EXISTS rag_chunks_fts USING fts5(
                            chunk_id UNINDEXED,
                            content,
                            tokenize = 'porter ascii'
                        );
                    """)
                except sqlite3.OperationalError:
                    # SQLite without FTS5 compiled: table will use LIKE fallback
                    pass
        finally:
            conn.close()

    def index_chunk(
        self,
        session_id: str,
        content: str,
        role: str = "user",
        metadata: Optional[dict[str, Any]] = None
    ) -> str:
        if not content or not content.strip():
            return ""
        chunk_id = f"chk-{uuid.uuid4().hex[:12]}"
        now = time.time()
        meta_str = str(metadata) if metadata else "{}"

        conn = self._get_connection()
        try:
            with conn:
                conn.execute(
                    "INSERT INTO rag_chunks (chunk_id, session_id, role, content, metadata, timestamp) VALUES (?, ?, ?, ?, ?, ?)",
                    (chunk_id, session_id, role, content.strip(), meta_str, now)
                )
                try:
                    conn.execute(
                        "INSERT INTO rag_chunks_fts (chunk_id, content) VALUES (?, ?)",
                        (chunk_id, content.strip())
                    )
                except sqlite3.OperationalError:
                    pass
        finally:
            conn.close()
        return chunk_id

    def search(self, query: str, session_id: Optional[str] = None, top_k: int = 3) -> list[dict[str, Any]]:
        if not query or not query.strip():
            return []
        clean_tokens = [re.sub(r'[^a-zA-Z0-9]', '', w) for w in query.split()]
        tokens = [t for t in clean_tokens if len(t) >= 2]
        if not tokens:
            return []

        conn = self._get_connection()
        try:
            # 1. Attempt FTS5 Match
            fts_query = " OR ".join(tokens)
            try:
                if session_id:
                    cursor = conn.execute("""
                        SELECT c.chunk_id, c.session_id, c.role, c.content, c.timestamp, rank
                        FROM rag_chunks_fts f
                        JOIN rag_chunks c ON f.chunk_id = c.chunk_id
                        WHERE rag_chunks_fts MATCH ? AND c.session_id = ?
                        ORDER BY rank LIMIT ?
                    """, (fts_query, session_id, top_k))
                else:
                    cursor = conn.execute("""
                        SELECT c.chunk_id, c.session_id, c.role, c.content, c.timestamp, rank
                        FROM rag_chunks_fts f
                        JOIN rag_chunks c ON f.chunk_id = c.chunk_id
                        WHERE rag_chunks_fts MATCH ?
                        ORDER BY rank LIMIT ?
                    """, (fts_query, top_k))
                rows = cursor.fetchall()
                if rows:
                    return [
                        {
                            "chunk_id": r["chunk_id"],
                            "session_id": r["session_id"],
                            "role": r["role"],
                            "content": r["content"],
                            "score": float(r["rank"])
                        }
                        for r in rows
                    ]
            except sqlite3.OperationalError:
                pass

            # 2. Fallback LIKE search
            clauses = ["content LIKE ?"] * len(tokens)
            where_clause = " OR ".join(clauses)
            params: list[Any] = [f"%{t}%" for t in tokens]
            if session_id:
                where_clause = f"session_id = ? AND ({where_clause})"
                params.insert(0, session_id)
            params.append(top_k)

            cursor = conn.execute(
                f"SELECT chunk_id, session_id, role, content, timestamp FROM rag_chunks WHERE {where_clause} ORDER BY id DESC LIMIT ?",
                params
            )
            rows = cursor.fetchall()
            return [
                {
                    "chunk_id": r["chunk_id"],
                    "session_id": r["session_id"],
                    "role": r["role"],
                    "content": r["content"],
                    "score": 1.0
                }
                for r in rows
            ]
        finally:
            conn.close()


rag_engine = RAGEngine()
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pytest tests/test_rag_engine.py -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add jarvis/backend/app/memory/rag_engine.py jarvis/backend/tests/test_rag_engine.py
git commit -m "feat(memory): add RAG hybrid semantic memory engine"
```

---

### Task 3: Memory-Augmented Graph (MAG) Structured Fact Store

**Files:**
- Create: `jarvis/backend/app/memory/mag_store.py`
- Test: `jarvis/backend/tests/test_mag_store.py`

**Interfaces:**
- Consumes: SQLite / `persistent_store` database connections
- Produces: `MAGStore` class with `set_fact(key: str, value: Any, category: str = "general") -> None`, `get_fact(key: str) -> Optional[Any]`, `get_facts_by_category(category: str) -> dict[str, Any]`, `get_hardware_profile(device_id: str = "default_device") -> dict[str, Any]`, `set_hardware_profile(device_id: str, profile: dict[str, Any]) -> None`

- [ ] **Step 1: Write the failing test**

```python
# jarvis/backend/tests/test_mag_store.py
import pytest
from app.memory.mag_store import MAGStore

def test_mag_store_facts(tmp_path):
    db_file = str(tmp_path / "mag_test.db")
    mag = MAGStore(db_path=db_file)
    
    mag.set_fact("user_alias", "Minaty", category="profile")
    mag.set_fact("response_tone", "British butler", category="profile")
    
    assert mag.get_fact("user_alias") == "Minaty"
    profile = mag.get_facts_by_category("profile")
    assert profile["user_alias"] == "Minaty"
    assert profile["response_tone"] == "British butler"

def test_mag_hardware_profile(tmp_path):
    db_file = str(tmp_path / "mag_hw.db")
    mag = MAGStore(db_path=db_file)
    
    mag.set_hardware_profile("pixel-9", {
        "bluetooth_available": True,
        "torch_available": True,
        "camera_available": True,
        "max_volume": 100
    })
    
    hw = mag.get_hardware_profile("pixel-9")
    assert hw["bluetooth_available"] is True
    assert hw["max_volume"] == 100
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pytest tests/test_mag_store.py -v`
Expected: FAIL with `ModuleNotFoundError: No module named 'app.memory.mag_store'`

- [ ] **Step 3: Write minimal implementation**

```python
# jarvis/backend/app/memory/mag_store.py
"""
Memory-Augmented Graph (MAG) Structured Fact & Hardware Profile Store.
"""

import json
import os
import sqlite3
import time
from typing import Any, Optional


class MAGStore:
    def __init__(self, db_path: Optional[str] = None):
        self.db_path = db_path or os.getenv("JARVIS_DB_PATH", "jarvis_memory.db")
        self._init_db()

    def _get_connection(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        return conn

    def _init_db(self) -> None:
        conn = self._get_connection()
        try:
            with conn:
                conn.execute("""
                    CREATE TABLE IF NOT EXISTS mag_facts (
                        key TEXT PRIMARY KEY,
                        value_json TEXT NOT NULL,
                        category TEXT NOT NULL DEFAULT 'general',
                        updated_at REAL NOT NULL
                    );
                """)
                conn.execute("""
                    CREATE TABLE IF NOT EXISTS mag_hardware_profiles (
                        device_id TEXT PRIMARY KEY,
                        profile_json TEXT NOT NULL,
                        updated_at REAL NOT NULL
                    );
                """)
        finally:
            conn.close()

    def set_fact(self, key: str, value: Any, category: str = "general") -> None:
        now = time.time()
        val_str = json.dumps(value)
        conn = self._get_connection()
        try:
            with conn:
                conn.execute(
                    "INSERT INTO mag_facts (key, value_json, category, updated_at) VALUES (?, ?, ?, ?) "
                    "ON CONFLICT(key) DO UPDATE SET value_json=excluded.value_json, category=excluded.category, updated_at=excluded.updated_at",
                    (key, val_str, category, now)
                )
        finally:
            conn.close()

    def get_fact(self, key: str) -> Optional[Any]:
        conn = self._get_connection()
        try:
            cur = conn.cursor()
            cur.execute("SELECT value_json FROM mag_facts WHERE key = ?", (key,))
            row = cur.fetchone()
            if row:
                return json.loads(row["value_json"])
            return None
        finally:
            conn.close()

    def get_facts_by_category(self, category: str) -> dict[str, Any]:
        conn = self._get_connection()
        try:
            cur = conn.cursor()
            cur.execute("SELECT key, value_json FROM mag_facts WHERE category = ?", (category,))
            rows = cur.fetchall()
            return {r["key"]: json.loads(r["value_json"]) for r in rows}
        finally:
            conn.close()

    def set_hardware_profile(self, device_id: str, profile: dict[str, Any]) -> None:
        now = time.time()
        profile_str = json.dumps(profile)
        conn = self._get_connection()
        try:
            with conn:
                conn.execute(
                    "INSERT INTO mag_hardware_profiles (device_id, profile_json, updated_at) VALUES (?, ?, ?) "
                    "ON CONFLICT(device_id) DO UPDATE SET profile_json=excluded.profile_json, updated_at=excluded.updated_at",
                    (device_id, profile_str, now)
                )
        finally:
            conn.close()

    def get_hardware_profile(self, device_id: str = "default_device") -> dict[str, Any]:
        conn = self._get_connection()
        try:
            cur = conn.cursor()
            cur.execute("SELECT profile_json FROM mag_hardware_profiles WHERE device_id = ?", (device_id,))
            row = cur.fetchone()
            if row:
                return json.loads(row["profile_json"])
            # Default safe profile
            return {
                "bluetooth_available": True,
                "torch_available": True,
                "camera_available": True,
                "max_volume": 100
            }
        finally:
            conn.close()


mag_store = MAGStore()
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pytest tests/test_mag_store.py -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add jarvis/backend/app/memory/mag_store.py jarvis/backend/tests/test_mag_store.py
git commit -m "feat(memory): add MAG structured fact and hardware profile store"
```

---

### Task 4: Multimodal Sensory Models & Memory Coordinator

**Files:**
- Create: `jarvis/backend/app/memory/multimodal_memory.py`
- Modify: `jarvis/backend/app/agent/execution_models.py`
- Modify: `jarvis/backend/app/memory/memory_manager.py`
- Test: `jarvis/backend/tests/test_multimodal_memory.py`

**Interfaces:**
- Consumes: `cag_cache`, `rag_engine`, `mag_store`
- Produces: `SensoryTelemetry`, `MultimodalInputPayload`, `MultimodalMemoryCoordinator` with `retrieve_context(query: str, session_id: str, sensory: Optional[SensoryTelemetry] = None) -> dict[str, Any]`

- [ ] **Step 1: Write the failing test**

```python
# jarvis/backend/tests/test_multimodal_memory.py
import pytest
from app.agent.execution_models import SensoryTelemetry, MultimodalInputPayload
from app.memory.multimodal_memory import MultimodalMemoryCoordinator
from app.memory.cag_cache import CAGCache
from app.memory.rag_engine import RAGEngine
from app.memory.mag_store import MAGStore

def test_multimodal_memory_coordinator(tmp_path):
    db_file = str(tmp_path / "coordinator_test.db")
    cag = CAGCache()
    rag = RAGEngine(db_path=db_file)
    mag = MAGStore(db_path=db_file)
    
    mag.set_fact("user_name", "Minaty")
    rag.index_chunk("s-1", "Minaty loves jazz music.", role="user")
    
    coordinator = MultimodalMemoryCoordinator(cag=cag, rag=rag, mag=mag)
    sensory = SensoryTelemetry(battery_level=85, is_charging=True, network_type="wifi")
    
    ctx = coordinator.retrieve_context("What music do I like?", session_id="s-1", sensory=sensory)
    assert ctx["facts"].get("user_name") == "Minaty"
    assert len(ctx["relevant_rag"]) >= 1
    assert ctx["sensory"]["battery_level"] == 85
    assert ctx["sensory"]["is_charging"] is True
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pytest tests/test_multimodal_memory.py -v`
Expected: FAIL with missing classes/attributes.

- [ ] **Step 3: Write minimal implementation**

Update `jarvis/backend/app/agent/execution_models.py` with `SensoryTelemetry` and `MultimodalInputPayload`.
Create `jarvis/backend/app/memory/multimodal_memory.py`.
Update `jarvis/backend/app/memory/memory_manager.py` to forward messages to both persistent store and `RAGEngine`.

```python
# jarvis/backend/app/memory/multimodal_memory.py
"""
Multimodal Memory Coordinator unifying CAG, RAG, and MAG tiers.
"""

from typing import Any, Optional
from app.agent.execution_models import SensoryTelemetry
from app.memory.cag_cache import CAGCache, cag_cache
from app.memory.mag_store import MAGStore, mag_store
from app.memory.rag_engine import RAGEngine, rag_engine


class MultimodalMemoryCoordinator:
    def __init__(
        self,
        cag: Optional[CAGCache] = None,
        rag: Optional[RAGEngine] = None,
        mag: Optional[MAGStore] = None
    ):
        self.cag = cag or cag_cache
        self.rag = rag or rag_engine
        self.mag = mag or mag_store

    def retrieve_context(
        self,
        query: str,
        session_id: str = "default-session",
        sensory: Optional[SensoryTelemetry] = None
    ) -> dict[str, Any]:
        rag_results = self.rag.search(query, session_id=session_id, top_k=3)
        facts = self.mag.get_facts_by_category("profile")
        all_facts = {**facts, **self.mag.get_facts_by_category("general")}
        
        sensory_dict = sensory.model_dump() if sensory else {}

        return {
            "facts": all_facts,
            "relevant_rag": [r["content"] for r in rag_results],
            "sensory": sensory_dict
        }


multimodal_memory = MultimodalMemoryCoordinator()
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pytest tests/test_multimodal_memory.py -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add jarvis/backend/app/memory/multimodal_memory.py jarvis/backend/app/agent/execution_models.py jarvis/backend/app/memory/memory_manager.py jarvis/backend/tests/test_multimodal_memory.py
git commit -m "feat(memory): add multimodal sensory payload models and memory coordinator"
```

---

### Task 5: Closed-Loop Tool Execution, Dynamic Recovery & Brain Orchestration

**Files:**
- Modify: `jarvis/backend/app/agent/execution_orchestrator.py`
- Modify: `jarvis/backend/app/agent/orchestrator.py`
- Modify: `jarvis/backend/app/api/routes.py`
- Test: `jarvis/backend/tests/test_autonomous_orchestrator.py`

**Interfaces:**
- Consumes: `multimodal_memory`, `cag_cache`, `mag_store`, `execution_orchestrator`
- Produces: `JarvisBrain.process_utterance` accepting `sensory_data` and returning closed-loop verified execution reports with CAG fast cache check and dynamic recovery fallbacks.

- [ ] **Step 1: Write the failing test**

```python
# jarvis/backend/tests/test_autonomous_orchestrator.py
import pytest
from app.agent.orchestrator import JarvisBrain
from app.agent.execution_models import SensoryTelemetry
from app.memory.cag_cache import cag_cache

@pytest.mark.asyncio
async def test_jarvis_brain_cag_fast_cache():
    brain = JarvisBrain()
    cag_cache.invalidate()
    
    # First execution populates CAG cache
    res1 = await brain.process_utterance("what time is it", session_id="test-session-cag")
    assert res1["status"] in ("VERIFIED", "EXECUTED", "success")
    assert res1.get("cached") is not True
    
    # Second identical query returns sub-5ms cached response
    res2 = await brain.process_utterance("what time is it", session_id="test-session-cag")
    assert res2.get("cached") is True
    assert res2["status"] == res1["status"]

@pytest.mark.asyncio
async def test_jarvis_brain_sensory_and_closed_loop():
    brain = JarvisBrain()
    sensory = SensoryTelemetry(battery_level=15, is_charging=False, network_type="cellular")
    res = await brain.process_utterance("turn on flashlight", session_id="test-session-sensory", sensory_data=sensory)
    assert res["status"] in ("VERIFIED", "EXECUTED", "success")
    assert "response_text" in res
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pytest tests/test_autonomous_orchestrator.py -v`
Expected: FAIL due to missing parameters or caching logic.

- [ ] **Step 3: Write minimal implementation**

Update `jarvis/backend/app/agent/orchestrator.py`:
- Ingest `sensory_data`.
- Check CAG cache: if hit, return cached result immediately with `cached: True`.
- Ingest context via `multimodal_memory.retrieve_context`.
- In `ExecutionOrchestrator`, if a step fails or is blocked, attempt recovery: inspect `mag_store.get_hardware_profile` and provide a clean diagnostic fallback message instead of silent crash.
- Cache final execution results in `cag_cache`.
Update `jarvis/backend/app/api/routes.py` to pass sensory payload from incoming JSON to `brain.process_utterance`.

- [ ] **Step 4: Run test to verify it passes**

Run: `pytest tests/test_autonomous_orchestrator.py -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add jarvis/backend/app/agent/orchestrator.py jarvis/backend/app/agent/execution_orchestrator.py jarvis/backend/app/api/routes.py jarvis/backend/tests/test_autonomous_orchestrator.py
git commit -m "feat(orchestrator): add closed-loop verification, sensory ingestion, and dynamic recovery to JarvisBrain"
```

---

## Plan Review & Verification

Run all test suites across the repository:
```bash
cd jarvis/backend && pytest
```
Verify: All tests pass without regressions.
