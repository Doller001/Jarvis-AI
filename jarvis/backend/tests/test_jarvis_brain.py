"""
Tests for JarvisBrain and Intent Resolver pipeline.
"""

import pytest
from app.agent.orchestrator import jarvis_brain


@pytest.mark.asyncio
async def test_jarvis_brain_safe_action():
    res = await jarvis_brain.process_utterance("Jarvis torch on", session_id="s1", request_id="r1")
    assert res["type"] == "command_result"
    assert res["action"] == "toggle_torch"
    assert res["parameters"] == {"state": "on"}


@pytest.mark.asyncio
async def test_jarvis_brain_risky_action():
    res = await jarvis_brain.process_utterance("Jarvis call Alice", session_id="s1", request_id="r2")
    assert res["type"] == "confirmation_request"
    assert res["action"] == "call_contact"
    assert "confirmation_token" in res
