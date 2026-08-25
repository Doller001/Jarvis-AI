"""
Tests for Supabase Database Connectivity and REST Client.
"""

from unittest.mock import MagicMock, patch
import pytest
from httpx import Response

from app.db.supabase_client import SupabaseClient
from app.memory.persistent_store import PersistentStore


def test_supabase_client_unconfigured(monkeypatch):
    monkeypatch.delenv("SUPABASE_URL", raising=False)
    monkeypatch.delenv("SUPABASE_KEY", raising=False)
    monkeypatch.delenv("SUPABASE_DATABASE_URL", raising=False)
    monkeypatch.delenv("DATABASE_URL", raising=False)

    client = SupabaseClient(supabase_url="", supabase_key="", database_url="")
    assert not client.is_active
    status = client.ping()
    assert status["status"] == "unconfigured"


def test_supabase_client_rest_ping_success(monkeypatch):
    client = SupabaseClient(
        supabase_url="https://testproject.supabase.co",
        supabase_key="test-anon-key"
    )
    assert client.is_rest_enabled
    assert client.is_active

    mock_resp = Response(status_code=200, json=[{"id": 1}])
    with patch.object(client._http_client, "get", return_value=mock_resp):
        status = client.ping()
        assert status["status"] == "connected"
        assert status["driver"] == "supabase_rest"
        assert status["table_exists"] is True


def test_supabase_client_rest_save_and_get_history():
    client = SupabaseClient(
        supabase_url="https://testproject.supabase.co",
        supabase_key="test-anon-key"
    )

    # Test save message
    mock_post_resp = Response(status_code=201, json=[{"id": 1}])
    with patch.object(client._http_client, "post", return_value=mock_post_resp) as mock_post:
        ok = client.save_conversation_message("session_1", "user", "Hello Supabase")
        assert ok is True
        mock_post.assert_called_once()

    # Test get history
    mock_get_resp = Response(
        status_code=200,
        json=[
            {"role": "assistant", "content": "Hi there!", "timestamp": 102.0},
            {"role": "user", "content": "Hello Supabase", "timestamp": 100.0}
        ]
    )
    with patch.object(client._http_client, "get", return_value=mock_get_resp):
        history = client.get_conversation_history("session_1", limit=5)
        assert len(history) == 2
        assert history[0]["role"] == "user"
        assert history[0]["content"] == "Hello Supabase"
        assert history[1]["role"] == "assistant"
        assert history[1]["content"] == "Hi there!"


def test_supabase_client_user_preferences():
    client = SupabaseClient(
        supabase_url="https://testproject.supabase.co",
        supabase_key="test-anon-key"
    )

    # Set preference
    mock_post_resp = Response(status_code=201)
    with patch.object(client._http_client, "post", return_value=mock_post_resp):
        ok = client.set_user_preference("voice_speed", "1.25")
        assert ok is True

    # Get preference
    mock_get_resp = Response(status_code=200, json=[{"value": "1.25"}])
    with patch.object(client._http_client, "get", return_value=mock_get_resp):
        val = client.get_user_preference("voice_speed")
        assert val == "1.25"


def test_persistent_store_with_supabase_integration():
    mock_supabase = MagicMock(spec=SupabaseClient)
    mock_supabase.is_active = True
    mock_supabase.save_conversation_message.return_value = True
    mock_supabase.get_conversation_history.return_value = [
        {"role": "user", "content": "Supabase message", "timestamp": 100.0}
    ]

    store = PersistentStore(supabase=mock_supabase)
    assert store.is_supabase is True

    store.save_message("sess_1", "user", "Supabase message")
    mock_supabase.save_conversation_message.assert_called_once_with("sess_1", "user", "Supabase message")

    hist = store.get_history("sess_1")
    assert len(hist) == 1
    assert hist[0]["content"] == "Supabase message"
