"""
Tests for Jarvis Tool Registry.
"""

from app.tools.registry import tool_registry


def test_tool_registry_schemas():
    schemas = tool_registry.get_llm_schemas()
    assert isinstance(schemas, list)
    assert len(schemas) > 0
    func_names = [s["function"]["name"] for s in schemas]
    assert "toggle_torch" in func_names
    assert "call_contact" in func_names
