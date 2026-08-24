"""
Tests for JarvisBrain and Intent Resolver pipeline.
"""

import pytest

from app.agent.orchestrator import (
    JARVIS_SYSTEM_PROMPT,
    build_system_prompt,
    jarvis_brain,
)
from app.llm.base import LLMResponse
from app.memory.memory_manager import memory_manager


def test_build_system_prompt_injects_history():
    sid = "history-test"
    memory_manager.record_user_message(sid, "hello")
    memory_manager.record_assistant_message(sid, "hi there")
    prompt = build_system_prompt(sid, "hello")
    assert prompt.startswith(JARVIS_SYSTEM_PROMPT)
    assert "Recent conversation" in prompt
    assert "assistant: hi there" in prompt
    assert "user: hello" not in prompt


def test_build_system_prompt_empty_history():
    prompt = build_system_prompt("no-such-session", "hello")
    assert prompt == JARVIS_SYSTEM_PROMPT


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


@pytest.mark.asyncio
async def test_jarvis_brain_rejects_unregistered_llm_action(monkeypatch):
    async def fake_reasoning(**_kwargs):
        return LLMResponse(text='{"action":"delete_everything"}', action="delete_everything")

    monkeypatch.setattr("app.agent.orchestrator.llm_gateway.generate_reasoning", fake_reasoning)
    res = await jarvis_brain.process_utterance("do something unusual", session_id="s2", request_id="r3")

    assert res["type"] == "error"
    assert res["code"] == "UNKNOWN_ACTION"
