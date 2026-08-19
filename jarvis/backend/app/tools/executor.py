"""
Tool Executor for Jarvis.
"""

import logging
from typing import Dict, Any

from app.tools.registry import tool_registry
from app.security.exceptions import ToolExecutionError

logger = logging.getLogger(__name__)


class ToolExecutor:
    async def execute_tool(self, tool_name: str, parameters: Dict[str, Any]) -> Dict[str, Any]:
        tool = tool_registry.get_tool(tool_name)
        if not tool:
            raise ToolExecutionError(tool_name, f"Tool '{tool_name}' is not registered.")

        logger.info(f"Jarvis executing tool '{tool_name}' with parameters {parameters}")
        return {
            "status": "success",
            "tool": tool_name,
            "result": f"Jarvis successfully executed '{tool_name}'",
            "parameters": parameters,
        }


tool_executor = ToolExecutor()
