"""
Tests for Retrieval-Augmented Generation (RAG) Semantic & Episodic Memory Engine.
"""

import sqlite3
import pytest
from app.memory.rag_engine import RAGEngine, rag_engine


def test_singleton_instance():
    assert isinstance(rag_engine, RAGEngine)


def test_rag_engine_index_and_search(tmp_path):
    db_file = str(tmp_path / "rag_test.db")
    rag = RAGEngine(db_path=db_file)

    chk1 = rag.index_chunk("session-1", "User prefers dark mode and British butler tone.", role="system", metadata={"source": "preferences"})
    chk2 = rag.index_chunk("session-1", "Turned on the living room lights at 8 PM.", role="assistant")
    chk3 = rag.index_chunk("session-2", "Playing Bohemian Rhapsody by Queen.", role="assistant")

    assert chk1.startswith("chk-")
    assert chk2.startswith("chk-")
    assert chk3.startswith("chk-")

    results = rag.search("dark mode butler", session_id="session-1", top_k=2)
    assert len(results) >= 1
    top = results[0]
    assert top["chunk_id"] == chk1
    assert top["session_id"] == "session-1"
    assert top["role"] == "system"
    assert "dark mode" in top["content"].lower()
    assert isinstance(top["score"], float)


def test_session_specific_vs_global_search(tmp_path):
    db_file = str(tmp_path / "rag_sessions.db")
    rag = RAGEngine(db_path=db_file)

    rag.index_chunk("session-A", "The meeting is scheduled at 10 AM tomorrow.", role="user")
    rag.index_chunk("session-B", "The meeting notes are stored in Google Drive.", role="assistant")

    # Session-specific search
    res_a = rag.search("meeting", session_id="session-A")
    assert len(res_a) == 1
    assert res_a[0]["session_id"] == "session-A"
    assert "10 AM" in res_a[0]["content"]

    res_b = rag.search("meeting", session_id="session-B")
    assert len(res_b) == 1
    assert res_b[0]["session_id"] == "session-B"
    assert "Google Drive" in res_b[0]["content"]

    # Global search across all sessions
    res_global = rag.search("meeting", session_id=None, top_k=5)
    assert len(res_global) == 2
    session_ids = {r["session_id"] for r in res_global}
    assert session_ids == {"session-A", "session-B"}


def test_empty_and_whitespace_queries(tmp_path):
    db_file = str(tmp_path / "rag_empty.db")
    rag = RAGEngine(db_path=db_file)

    rag.index_chunk("session-1", "Some useful chunk of knowledge.", role="user")

    assert rag.search("") == []
    assert rag.search("   ") == []
    assert rag.search("\n\t  ") == []
    assert rag.search("??? !!!") == []
    assert rag.search("nonexistent topic that does not match") == []

    # Empty chunk indexing returns empty string
    assert rag.index_chunk("session-1", "") == ""
    assert rag.index_chunk("session-1", "   ") == ""


def test_top_k_limits(tmp_path):
    db_file = str(tmp_path / "rag_topk.db")
    rag = RAGEngine(db_path=db_file)

    for i in range(5):
        rag.index_chunk("session-1", f"Jarvis automation task item number {i}", role="user")

    res_k2 = rag.search("automation task", session_id="session-1", top_k=2)
    assert len(res_k2) == 2

    res_k4 = rag.search("automation task", session_id="session-1", top_k=4)
    assert len(res_k4) == 4

    res_k0 = rag.search("automation task", session_id="session-1", top_k=0)
    assert res_k0 == []


def test_fallback_like_search(tmp_path):
    db_file = str(tmp_path / "rag_fallback.db")
    rag = RAGEngine(db_path=db_file)
    rag.has_fts = False  # Explicitly force LIKE fallback

    chk1 = rag.index_chunk("session-fb", "Battery level is currently at 42 percent.", role="system")
    chk2 = rag.index_chunk("session-fb", "Volume set to maximum level.", role="assistant")

    results = rag.search("battery level", session_id="session-fb", top_k=2)
    assert len(results) >= 1
    assert results[0]["chunk_id"] == chk1
    assert "Battery level" in results[0]["content"]
    assert isinstance(results[0]["score"], float)
    assert results[0]["score"] > 0


def test_special_characters_handling(tmp_path):
    db_file = str(tmp_path / "rag_special.db")
    rag = RAGEngine(db_path=db_file)

    rag.index_chunk("session-1", "Config key: user_settings -> dark-mode (enabled: true).", role="system")

    # Queries with punctuation, colons, hyphens should not crash FTS5
    results = rag.search("dark-mode: enabled (true)?", session_id="session-1")
    assert len(results) >= 1
    assert "dark-mode" in results[0]["content"]
