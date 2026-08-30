"""
Tests for Autonomous Agent Orchestrator:
- Context-Aware Generation (CAG) Fast Cache
- Multimodal Sensory Telemetry Ingestion
- Multimodal Context Synthesis & Prompt Augmentation
- Closed-Loop Tool Execution & Dynamic Recovery Fallback
- End-to-end /chat HTTP API with Multimodal Payloads
"""

import pytest
from httpx import ASGITransport, AsyncClient

from app.agent.execution_models import (
    ActionStatus,
    ExecutionPlan,
    MultimodalInputPayload,
    PlannedAction,
    SensoryTelemetry,
)
from app.agent.execution_orchestrator import execution_orchestrator
from app.agent.orchestrator import (
    JARVIS_SYSTEM_PROMPT,
    JarvisBrain,
    build_system_prompt,
    jarvis_brain,
)
from app.main import app
from app.memory.cag_cache import cag_cache
from app.memory.mag_store import mag_store
from app.memory.multimodal_memory import multimodal_memory
from app.memory.rag_engine import rag_engine
from app.security.device_registry import device_registry
from app.security.jwt_manager import jwt_manager


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
    assert res2["action"] == "get_time"


@pytest.mark.asyncio
async def test_jarvis_brain_cag_sensory_fingerprint_isolation():
    brain = JarvisBrain()
    cag_cache.invalidate()

    sensory1 = SensoryTelemetry(battery_level=80, network_type="wifi")
    sensory2 = SensoryTelemetry(battery_level=10, network_type="cellular")

    # Run query with sensory1
    res1 = await brain.process_utterance(
        "what time is it",
        session_id="test-session-sensory-cag",
        sensory_data=sensory1,
    )
    assert res1.get("cached") is not True

    # Query with sensory2 should miss the cache due to different fingerprint
    res2 = await brain.process_utterance(
        "what time is it",
        session_id="test-session-sensory-cag",
        sensory_data=sensory2,
    )
    assert res2.get("cached") is not True

    # Query with sensory1 again should hit cache
    res3 = await brain.process_utterance(
        "what time is it",
        session_id="test-session-sensory-cag",
        sensory_data=sensory1,
    )
    assert res3.get("cached") is True


def test_build_system_prompt_multimodal_context_augmentation():
    sid = "prompt-test-session"
    mag_store.set_fact("user_alias", "Tony", category="profile")
    rag_engine.index_chunk(sid, "Tony prefers minimalist responses", role="user")

    sensory = SensoryTelemetry(battery_level=45, network_type="wifi", volume_level=60)
    context = multimodal_memory.retrieve_context("preference", session_id=sid, sensory=sensory)

    prompt = build_system_prompt(sid, "preference", context=context)

    assert prompt.startswith(JARVIS_SYSTEM_PROMPT)
    assert "Known Facts & User Profile:" in prompt
    assert "user_alias: Tony" in prompt
    assert "Live Sensory Telemetry:" in prompt
    assert "battery_level: 45" in prompt
    assert "network_type: wifi" in prompt


@pytest.mark.asyncio
async def test_dynamic_recovery_hardware_profile_unavailable():
    sid = "session-no-torch"
    mag_store.set_hardware_profile(
        sid,
        {
            "torch_available": False,
            "bluetooth_available": True,
            "max_volume": 100,
        },
    )

    plan = ExecutionPlan(
        request_id="req-hw-1",
        session_id=sid,
        utterance="turn on torch",
        actions=[
            PlannedAction(
                id="cmd-torch-fail",
                tool="toggle_torch",
                parameters={"state": "on"},
            )
        ],
    )

    report = await execution_orchestrator.execute_plan(plan)
    assert report.status == "failed"
    assert report.verified_actions == 0
    assert (
        "torch" in report.message.lower()
        or "unavailable" in report.message.lower()
        or "not supported" in report.message.lower()
    )


@pytest.mark.asyncio
async def test_dynamic_recovery_volume_limit_exceeded():
    sid = "session-vol-limit"
    mag_store.set_hardware_profile(
        sid,
        {
            "max_volume": 80,
            "bluetooth_available": True,
            "torch_available": True,
        },
    )

    plan = ExecutionPlan(
        request_id="req-vol-1",
        session_id=sid,
        utterance="set volume to 95",
        actions=[
            PlannedAction(
                id="cmd-vol-exceed",
                tool="set_volume",
                parameters={"level": 95},
            )
        ],
    )

    report = await execution_orchestrator.execute_plan(plan)
    assert report.status == "failed"
    assert "exceeds" in report.message.lower() or "limit" in report.message.lower() or "80" in report.message


@pytest.mark.asyncio
async def test_dynamic_recovery_offline_device_diagnostic():
    sid = "session-offline-rec"
    plan = ExecutionPlan(
        request_id="req-offline-rec",
        session_id=sid,
        utterance="open settings",
        actions=[
            PlannedAction(
                id="cmd-offline-app",
                tool="open_app",
                parameters={"app_name": "settings"},
            )
        ],
    )

    sensory = SensoryTelemetry(battery_level=8, network_type="offline")
    report = await execution_orchestrator.execute_plan(plan, sensory_data=sensory)

    assert report.status == "failed"
    assert "not connected" in report.message.lower() or "offline" in report.message.lower()


@pytest.mark.asyncio
async def test_chat_http_multimodal_payload_and_cag():
    cag_cache.invalidate()
    device_registry.register_device("test-auto-phone", "Pixel", "14", device_id="http-auto-dev-1")
    tokens = jwt_manager.create_token_pair("http-auto-dev-1")
    headers = {"Authorization": f"Bearer {tokens.access_token}"}

    payload = {
        "text": "what time is it",
        "session_id": "http-auto-s1",
        "request_id": "req-auto-http-1",
        "sensory_data": {
            "battery_level": 88,
            "network_type": "wifi",
            "volume_level": 75,
        },
    }

    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        # First request - cache miss
        res1 = await client.post("/api/v1/chat", json=payload, headers=headers)
        assert res1.status_code == 200
        data1 = res1.json()
        assert data1["type"] == "command_result"
        assert data1["action"] == "get_time"
        assert data1.get("cached") is not True

        # Second request - CAG cache hit
        payload["request_id"] = "req-auto-http-2"
        res2 = await client.post("/api/v1/chat", json=payload, headers=headers)
        assert res2.status_code == 200
        data2 = res2.json()
        assert data2.get("cached") is True
        assert data2["action"] == "get_time"
