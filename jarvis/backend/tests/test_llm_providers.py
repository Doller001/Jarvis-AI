"""
Tests for LLM Registry and Provider Discovery in Jarvis.
"""

import pytest

from app.llm.base import extract_action_and_params
from app.llm.providers.ollama import OllamaProvider
from app.llm.registry import LLMRegistry


@pytest.mark.asyncio
async def test_ollama_unreachable_handling():
    p = OllamaProvider(base_url="http://127.0.0.1:59999")
    assert await p.health_check() is False
    assert await p.validate_key() is False
    assert await p.list_models() == []


def test_registry_active_selection():
    reg = LLMRegistry()
    res = reg.set_active_provider_and_model("groq", "llama-3.3-70b-versatile")
    assert res is True
    sel = reg.get_active_selection()
    assert sel["provider"] == "groq"
    assert sel["model"] == "llama-3.3-70b-versatile"


def test_extract_action_and_params_markdown_and_raw():
    # Markdown json block
    text1 = '```json\n{"action": "toggle_torch", "parameters": {"state": "on"}, "confidence": 0.98}\n```'
    action1, params1, conf1 = extract_action_and_params(text1)
    assert action1 == "toggle_torch"
    assert params1 == {"state": "on"}
    assert conf1 == 0.98

    # Raw JSON
    text2 = '{"action": "open_app", "parameters": {"app_name": "YouTube"}}'
    action2, params2, _ = extract_action_and_params(text2)
    assert action2 == "open_app"
    assert params2 == {"app_name": "YouTube"}

    # Conversational text without JSON action
    text3 = "The capital of France is Paris."
    action3, _, _ = extract_action_and_params(text3)
    assert action3 is None
