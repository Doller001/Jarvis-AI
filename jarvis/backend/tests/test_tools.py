"""
Tests for Jarvis Tool Registry.
"""

import pytest

from app.tools.executor import tool_executor
from app.tools.registry import tool_registry


def test_tool_registry_schemas():
    schemas = tool_registry.get_llm_schemas()
    assert isinstance(schemas, list)
    assert len(schemas) > 0
    func_names = [s["function"]["name"] for s in schemas]
    assert "toggle_torch" in func_names
    assert "call_contact" in func_names


@pytest.mark.asyncio
async def test_set_volume_keeps_explicit_zero():
    result = await tool_executor.execute_tool("set_volume", {"level": 0})
    assert result["result"] == "Volume adjusted to 0%."
