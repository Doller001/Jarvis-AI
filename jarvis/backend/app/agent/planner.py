"""
Task Planner and Risk Policy for Jarvis.
"""

from typing import Dict, Any, List
from app.tools.registry import tool_registry

RISKY_ACTIONS = {
    "call_contact",
    "whatsapp_send",
    "send_sms",
    "delete_file",
    "install_apk",
    "change_security_settings"
}


class RiskPolicy:
    def is_auto_executable(self, action: str) -> bool:
        tool = tool_registry.get_tool(action)
        if tool:
            return not tool.requires_confirmation
        return action not in RISKY_ACTIONS


class TaskPlanner:
    def create_plan(self, action: str, parameters: Dict[str, Any]) -> Dict[str, Any]:
        return {
            "plan_id": f"plan-{action}",
            "action": action,
            "steps": [{"step_id": 1, "tool_name": action, "parameters": parameters}]
        }


risk_policy = RiskPolicy()
task_planner = TaskPlanner()
