"""
Tests for LLM Registry and Provider Discovery in Jarvis.
"""

import pytest
from app.llm.registry import llm_registry, LLMRegistry
from app.llm.providers.ollama import OllamaProvider


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
