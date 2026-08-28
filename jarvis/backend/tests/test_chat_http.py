"""
Tests for the HTTP chat endpoint.
"""

import pytest
from httpx import ASGITransport, AsyncClient

from app.main import app


@pytest.mark.asyncio
async def test_chat_http_endpoint():
    from app.security.device_registry import device_registry
    from app.security.jwt_manager import jwt_manager

    device_registry.register_device("test-phone", "Pixel", "14", device_id="http-dev-1")
    tokens = jwt_manager.create_token_pair("http-dev-1")
    headers = {"Authorization": f"Bearer {tokens.access_token}"}

    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        res = await client.post(
            "/api/v1/chat",
            json={"text": "Jarvis torch on", "session_id": "http-s1"},
            headers=headers,
        )
    assert res.status_code == 200
    data = res.json()
    assert data["type"] == "command_result"
    assert data["action"] == "toggle_torch"


@pytest.mark.asyncio
async def test_chat_http_missing_text():
    from app.security.device_registry import device_registry
    from app.security.jwt_manager import jwt_manager

    device_registry.register_device("test-phone", "Pixel", "14", device_id="http-dev-1")
    tokens = jwt_manager.create_token_pair("http-dev-1")
    headers = {"Authorization": f"Bearer {tokens.access_token}"}

    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        res = await client.post("/api/v1/chat", json={}, headers=headers)
    assert res.status_code == 422


@pytest.mark.asyncio
async def test_chat_http_unauthorized():
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        res = await client.post("/api/v1/chat", json={"text": "hello"})
    assert res.status_code == 401

