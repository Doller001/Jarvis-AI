"""
Tests for Memory Persistent Store (MongoDB Atlas, Supabase PostgreSQL, SQLite).
"""

import pytest
from unittest.mock import MagicMock
from app.memory.persistent_store import PersistentStore


def test_sqlite_fallback_memory(tmp_path, monkeypatch):
    db_file = str(tmp_path / "test_memory.db")
    monkeypatch.setenv("JARVIS_DB_PATH", db_file)
    monkeypatch.delenv("MONGODB_URI", raising=False)
    monkeypatch.delenv("DATABASE_URL", raising=False)

    store = PersistentStore()
    assert not store.is_mongodb
    assert not store.is_postgres

    session_id = "test_session_123"
    store.save_message(session_id, "user", "Hello Jarvis")
    store.save_message(session_id, "assistant", "Hello! How can I help?")

    history = store.get_history(session_id)
    assert len(history) == 2
    assert history[0]["role"] == "user"
    assert history[0]["content"] == "Hello Jarvis"
    assert history[1]["role"] == "assistant"
    assert history[1]["content"] == "Hello! How can I help?"


def test_mock_mongodb_atlas(monkeypatch):
    mock_client = MagicMock()
    mock_db = MagicMock()
    mock_client.get_default_database.return_value.name = "jarvis"
    mock_client.__getitem__.return_value = mock_db
    
    mock_conversations = MagicMock()
    mock_db.conversations = mock_conversations
    # Sort DESC returns latest first (101.0 assistant, then 100.0 user)
    mock_conversations.find.return_value.sort.return_value.limit.return_value = [
        {"role": "assistant", "content": "Hello", "timestamp": 101.0},
        {"role": "user", "content": "Hi", "timestamp": 100.0}
    ]

    monkeypatch.setenv("MONGODB_URI", "mongodb+srv://user:pass@cluster.mongodb.net/jarvis")
    monkeypatch.setattr("pymongo.MongoClient", lambda uri, **kwargs: mock_client)

    store = PersistentStore()
    assert store.is_mongodb

    store.save_message("s1", "user", "Hi")
    mock_conversations.insert_one.assert_called_once()

    history = store.get_history("s1")
    assert len(history) == 2
    assert history[0]["content"] == "Hi"
    assert history[1]["content"] == "Hello"
