"""
Tests for the HTTP chat endpoint.
"""

import pytest
from fastapi.testclient import TestClient

from app.main import app


@pytest.fixture
def client():
    return TestClient(app)


def test_chat_http_endpoint(client):
    res = client.post("/api/v1/chat", json={"text": "Jarvis torch on", "session_id": "http-s1"})
    assert res.status_code == 200
    data = res.json()
    assert data["type"] == "command_result"
    assert data["action"] == "toggle_torch"


def test_chat_http_missing_text(client):
    res = client.post("/api/v1/chat", json={})
    assert res.status_code == 422