"""
Unit and Integration tests for OpenAI-compatible endpoints & universal chat convenience routes.
"""

import pytest
from httpx import ASGITransport, AsyncClient

from app.main import app


@pytest.mark.asyncio
async def test_providers_and_models_unauthenticated():
    """Verify that provider and model discovery are accessible without 401."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        # GET /api/v1/providers
        res_providers = await client.get("/api/v1/providers")
        assert res_providers.status_code == 200
        data_providers = res_providers.json()
        assert isinstance(data_providers, list)

        # GET /api/v1/models
        res_models = await client.get("/api/v1/models")
        assert res_models.status_code == 200
        data_models = res_models.json()
        assert "models" in data_models
        assert "active_selection" in data_models


@pytest.mark.asyncio
async def test_openai_chat_completions_post():
    """Verify standard OpenAI POST /v1/chat/completions and /chat/completions."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        for endpoint in ["/v1/chat/completions", "/chat/completions"]:
            res = await client.post(
                endpoint,
                json={
                    "model": "nvidia/llama-3.1-nemotron-70b-instruct",
                    "messages": [
                        {"role": "user", "content": "turn on flashlight"}
                    ]
                }
            )
            assert res.status_code == 200
            data = res.json()
            assert data["object"] == "chat.completion"
            assert "choices" in data
            assert len(data["choices"]) > 0
            assert "message" in data["choices"][0]
            assert "content" in data["choices"][0]["message"]
            assert "usage" in data


@pytest.mark.asyncio
async def test_openai_chat_completions_get():
    """Verify OpenAI GET /v1/chat/completions probing and query."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        # Empty probe
        res_probe = await client.get("/v1/chat/completions")
        assert res_probe.status_code == 200
        assert res_probe.json().get("status") == "healthy"

        # Query param execution
        res_q = await client.get("/v1/chat/completions?q=turn+on+torch")
        assert res_q.status_code == 200
        assert "choices" in res_q.json()


@pytest.mark.asyncio
async def test_openai_models_list():
    """Verify GET /v1/models and GET /models OpenAI compatibility."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        for endpoint in ["/v1/models", "/models"]:
            res = await client.get(endpoint)
            assert res.status_code == 200
            data = res.json()
            assert data.get("object") == "list"
            assert isinstance(data.get("data"), list)
            assert len(data["data"]) > 0


@pytest.mark.asyncio
async def test_universal_convenience_endpoints():
    """Verify GET and POST on /chat, /ask, /query, /generate, /completions, /conversation, /message, /send."""
    endpoints = [
        "/chat", "/ask", "/query", "/generate",
        "/completions", "/conversation", "/message", "/send"
    ]
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        for ep in endpoints:
            # 1. Probing GET without params -> 200 OK
            res_probe = await client.get(ep)
            assert res_probe.status_code == 200

            # 2. GET with query param ?q=... -> 200 OK
            res_q = await client.get(f"{ep}?q=turn+on+torch")
            assert res_q.status_code == 200

            # 3. POST with json -> 200 OK
            res_post = await client.post(ep, json={"text": "turn on torch"})
            assert res_post.status_code == 200
