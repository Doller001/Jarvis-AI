"""
Tests for Multi-Action Planning, Execution Orchestrator, Device Verification, and Real Health Endpoints.
"""

import pytest
from httpx import ASGITransport, AsyncClient

from app.agent.execution_models import ActionStatus
from app.agent.execution_orchestrator import execution_orchestrator
from app.agent.planner import task_planner
from app.main import app
from app.realtime.command_registry import command_registry


@pytest.mark.asyncio
async def test_multi_action_planner_decomposition():
    """Verifies that 'open youtube and play headlight' generates 2 dependent actions."""
    plan = task_planner.plan_utterance("open youtube and play headlight song", session_id="s-test", request_id="r-multi-1")
    assert len(plan.actions) == 2
    assert plan.actions[0].tool == "open_app"
    assert plan.actions[0].parameters.get("app_name") == "youtube"
    assert plan.actions[0].verification is not None
    assert plan.actions[0].verification.type == "foreground_app"

    assert plan.actions[1].tool == "play_media_search"
    assert "headlight" in plan.actions[1].parameters.get("query", "").lower()
    assert plan.actions[1].depends_on == [plan.actions[0].id]


@pytest.mark.asyncio
async def test_execution_orchestrator_multi_action_success():
    """Verifies that execution orchestrator executes server and local actions and returns verified status."""
    plan = task_planner.plan_utterance("get time", session_id="s-test", request_id="r-time-1")
    report = await execution_orchestrator.execute_plan(plan)

    assert report.status == "success"
    assert report.total_actions == 1
    assert report.verified_actions == 1
    assert "Current time is" in report.message


@pytest.mark.asyncio
async def test_command_registry_idempotency_and_cancellation():
    """Verifies that cancelled requests abort future command execution."""
    command_registry.cancel_request("req-cancel-123")
    assert command_registry.is_cancelled("req-cancel-123")


@pytest.mark.asyncio
async def test_real_health_endpoints():
    """Verifies /health/live, /health/ready, and /api/v1/health/dependencies."""
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        # 1. Liveness
        live_res = await client.get("/health/live")
        assert live_res.status_code == 200
        assert live_res.json()["status"] == "alive"

        # 2. Readiness
        ready_res = await client.get("/health/ready")
        assert ready_res.status_code == 200
        assert ready_res.json()["status"] == "ready"

        # 3. Dependencies
        dep_res = await client.get("/api/v1/health/dependencies")
        assert dep_res.status_code == 200
        data = dep_res.json()
        assert data["backend"] == "ok"
        assert "available_llm_providers" in data
