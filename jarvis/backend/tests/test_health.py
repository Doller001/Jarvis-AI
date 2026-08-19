"""
Tests for Jarvis Health API endpoints.
"""

import pytest
from httpx import ASGITransport, AsyncClient
from app.main import app


@pytest.mark.asyncio
async def test_jarvis_health_endpoints():
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        resp = await client.get("/health")
        assert resp.status_code == 200
        assert resp.json()["service"] == "jarvis-backend"

        resp_v1 = await client.get("/api/v1/health")
        assert resp_v1.status_code == 200
        assert resp_v1.json()["service"] == "jarvis-backend"
