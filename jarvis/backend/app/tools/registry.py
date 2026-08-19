"""
Jarvis Canonical Tool Registry.
"""

from typing import Dict, Any, List, Optional
from pydantic import BaseModel, Field


class ToolParameter(BaseModel):
    type: str
    description: str
    required: bool = True
    enum: Optional[List[str]] = None


class ToolDefinition(BaseModel):
    name: str
    description: str
    risk_level: str = "safe"
    requires_confirmation: bool = False
    idempotent: bool = True
    platform: str = "android"
    parameters: Dict[str, ToolParameter] = Field(default_factory=dict)
    permissions: List[str] = Field(default_factory=list)


class ToolRegistry:
    def __init__(self) -> None:
        self._tools: Dict[str, ToolDefinition] = {}
        self._register_default_tools()

    def register(self, tool: ToolDefinition) -> None:
        self._tools[tool.name] = tool

    def get_tool(self, name: str) -> Optional[ToolDefinition]:
        return self._tools.get(name)

    def list_tools(self) -> List[ToolDefinition]:
        return list(self._tools.values())

    def get_llm_schemas(self) -> List[Dict[str, Any]]:
        schemas = []
        for tool in self._tools.values():
            properties = {}
            required = []
            for param_name, param in tool.parameters.items():
                prop_def = {"type": param.type, "description": param.description}
                if param.enum:
                    prop_def["enum"] = param.enum
                properties[param_name] = prop_def
                if param.required:
                    required.append(param_name)

            schemas.append({
                "type": "function",
                "function": {
                    "name": tool.name,
                    "description": tool.description,
                    "parameters": {
                        "type": "object",
                        "properties": properties,
                        "required": required
                    }
                }
            })
        return schemas

    def _register_default_tools(self) -> None:
        # Safe device tools
        self.register(ToolDefinition(
            name="toggle_wifi", description="Toggle Wi-Fi state.", risk_level="safe",
            parameters={"state": ToolParameter(type="string", description="on or off", enum=["on", "off"])}
        ))
        self.register(ToolDefinition(
            name="toggle_bluetooth", description="Toggle Bluetooth state.", risk_level="safe",
            parameters={"state": ToolParameter(type="string", description="on or off", enum=["on", "off"])}
        ))
        self.register(ToolDefinition(
            name="toggle_torch", description="Toggle flashlight torch on/off.", risk_level="safe",
            parameters={"state": ToolParameter(type="string", description="on or off", enum=["on", "off"])}
        ))
        self.register(ToolDefinition(
            name="set_volume", description="Set device volume.", risk_level="safe",
            parameters={"level": ToolParameter(type="integer", description="0-100 percentage")}
        ))
        self.register(ToolDefinition(
            name="get_time", description="Get current local time.", risk_level="safe"
        ))
        self.register(ToolDefinition(
            name="get_battery_level", description="Get battery level percentage.", risk_level="safe"
        ))
        self.register(ToolDefinition(
            name="open_app", description="Open application.", risk_level="safe",
            parameters={"app_name": ToolParameter(type="string", description="Application name")}
        ))
        self.register(ToolDefinition(
            name="read_screen", description="Read active screen nodes via Accessibility.", risk_level="safe"
        ))
        self.register(ToolDefinition(
            name="web_search", description="Perform live web search to answer queries with up-to-date grounded information.", risk_level="safe",
            platform="backend", parameters={"query": ToolParameter(type="string", description="Search query string")}
        ))
        self.register(ToolDefinition(
            name="analyze_image", description="Analyze image or screenshot contents using multimodal vision model.", risk_level="safe",
            platform="backend", parameters={"image_url_or_base64": ToolParameter(type="string", description="Base64 image data or URL"), "prompt": ToolParameter(type="string", description="Question about the image", required=False)}
        ))

        # Risky confirmation tools
        self.register(ToolDefinition(
            name="call_contact", description="Make phone call.", risk_level="confirmation", requires_confirmation=True,
            parameters={"contact_name": ToolParameter(type="string", description="Contact name or number")}
        ))
        self.register(ToolDefinition(
            name="send_sms", description="Send SMS text message.", risk_level="confirmation", requires_confirmation=True,
            parameters={"recipient": ToolParameter(type="string", description="Recipient"), "message": ToolParameter(type="string", description="Message text")}
        ))
        self.register(ToolDefinition(
            name="whatsapp_send", description="Send WhatsApp message.", risk_level="confirmation", requires_confirmation=True,
            parameters={"contact_name": ToolParameter(type="string", description="Contact name"), "message": ToolParameter(type="string", description="Message text")}
        ))


tool_registry = ToolRegistry()
