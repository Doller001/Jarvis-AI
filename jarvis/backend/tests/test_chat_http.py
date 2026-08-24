"""
Tests for the HTTP chat endpoint.
"""

import pytest
from httpx import ASGITransport, AsyncClient

from app.main import app


@pytest.mark.asyncio
async def test_chat_http_endpoint():
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        res = await client.post("/api/v1/chat", json={"text": "Jarvis torch on", "session_id": "http-s1"})
    assert res.status_code == 200
    data = res.json()
    assert data["type"] == "command_result"
    assert data["action"] == "toggle_torch"


@pytest.mark.asyncio
async def test_chat_http_missing_text():
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        res = await client.post("/api/v1/chat", json={})
    assert res.status_code == 422
