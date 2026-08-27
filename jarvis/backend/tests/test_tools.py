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
    assert "set_brightness" in func_names
    assert "toggle_dnd" in func_names
    assert "set_ringer_mode" in func_names
    assert "toggle_rotation_lock" in func_names
    assert "take_screenshot" in func_names
    assert "run_routine" in func_names
    assert "set_alarm" in func_names
    assert "set_timer" in func_names
    assert "set_reminder" in func_names
    assert "get_location" in func_names
    assert "navigate_to" in func_names
    assert "read_calendar" in func_names
    assert "get_daily_briefing" in func_names
    assert "lock_screen" in func_names


@pytest.mark.asyncio
async def test_set_volume_keeps_explicit_zero():
    result = await tool_executor.execute_tool("set_volume", {"level": 0})
    assert result["result"] == "Volume adjusted to 0%."


@pytest.mark.asyncio
async def test_new_tools_execution():
    # Test brightness
    res = await tool_executor.execute_tool("set_brightness", {"level": 75})
    assert res["status"] == "success"
    assert "75%" in res["result"]

    # Test DND
    res = await tool_executor.execute_tool("toggle_dnd", {"state": "on"})
    assert res["status"] == "success"
    assert "Do Not Disturb on" in res["result"]

    # Test ringer mode
    res = await tool_executor.execute_tool("set_ringer_mode", {"mode": "silent"})
    assert res["status"] == "success"
    assert "silent" in res["result"]

    # Test routine
    res = await tool_executor.execute_tool("run_routine", {"routine": "morning"})
    assert res["status"] == "success"
    assert "morning routine" in res["result"]

    # Test alarm
    res = await tool_executor.execute_tool("set_alarm", {"hour": 8, "minute": 30})
    assert res["status"] == "success"
    assert "08:30" in res["result"]

    # Test timer
    res = await tool_executor.execute_tool("set_timer", {"seconds": 120})
    assert res["status"] == "success"
    assert "120s" in res["result"]

    # Test reminder
    res = await tool_executor.execute_tool("set_reminder", {"delay_minutes": 15, "message": "Meeting"})
    assert res["status"] == "success"
    assert "15 minutes" in res["result"]

    # Test lock screen
    res = await tool_executor.execute_tool("lock_screen", {})
    assert res["status"] == "success"
    assert "Locking device screen" in res["result"]

