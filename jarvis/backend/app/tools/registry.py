"""
Jarvis Canonical Tool Registry.
"""

from typing import Any

from pydantic import BaseModel, Field


class ToolParameter(BaseModel):
    type: str
    description: str
    required: bool = True
    enum: list[str] | None = None


class ToolDefinition(BaseModel):
    name: str
    description: str
    risk_level: str = "safe"
    requires_confirmation: bool = False
    idempotent: bool = True
    platform: str = "android"
    parameters: dict[str, ToolParameter] = Field(default_factory=dict)
    permissions: list[str] = Field(default_factory=list)


class ToolRegistry:
    def __init__(self) -> None:
        self._tools: dict[str, ToolDefinition] = {}
        self._register_default_tools()

    def register(self, tool: ToolDefinition) -> None:
        self._tools[tool.name] = tool

    def get_tool(self, name: str) -> ToolDefinition | None:
        return self._tools.get(name)

    def list_tools(self) -> list[ToolDefinition]:
        return list(self._tools.values())

    def get_llm_schemas(self) -> list[dict[str, Any]]:
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
        self.register(ToolDefinition(
            name="search_music",
            description=(
                "Semantic search over the local music library to find songs by mood, "
                "vibe, language, era, artist or free-form description "
                "(e.g. 'sad hindi song for late night', 'energetic party banger'). "
                "Use this before playing music so you know WHICH song to play."
            ),
            risk_level="safe", platform="backend",
            parameters={
                "query": ToolParameter(type="string", description="Natural-language description of the song or vibe wanted"),
                "limit": ToolParameter(type="integer", description="How many songs to return (default 5)", required=False),
                "language": ToolParameter(type="string", description="Filter by language, e.g. Hindi, English, Tamil", required=False),
                "mood": ToolParameter(type="string", description="Filter by mood, e.g. sad, romantic, party, energetic, motivational", required=False),
                "year_min": ToolParameter(type="integer", description="Earliest release year", required=False),
                "year_max": ToolParameter(type="integer", description="Latest release year", required=False),
            }
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

        # === New Phase 1+2 tools ===
        self.register(ToolDefinition(
            name="set_brightness",
            description="Set device screen brightness (0-100%).",
            risk_level="safe",
            parameters={"level": ToolParameter(type="integer", description="Brightness level 0-100")}
        ))
        self.register(ToolDefinition(
            name="toggle_dnd",
            description="Toggle Do Not Disturb mode on or off.",
            risk_level="safe",
            parameters={"state": ToolParameter(type="string", description="on or off", enum=["on", "off"])}
        ))
        self.register(ToolDefinition(
            name="set_ringer_mode",
            description="Set phone ringer mode to silent, vibrate, or normal.",
            risk_level="safe",
            parameters={"mode": ToolParameter(type="string", description="silent, vibrate, or normal", enum=["silent", "vibrate", "normal"])}
        ))
        self.register(ToolDefinition(
            name="toggle_rotation_lock",
            description="Enable or disable screen rotation lock.",
            risk_level="safe",
            parameters={"state": ToolParameter(type="string", description="on (locked) or off (auto-rotate)", enum=["on", "off"])}
        ))
        self.register(ToolDefinition(
            name="take_screenshot",
            description="Capture a screenshot of the current screen.",
            risk_level="safe"
        ))
        self.register(ToolDefinition(
            name="run_routine",
            description=(
                "Activate a preset multi-step device routine. "
                "Available routines: morning (brightness up, volume up, DND off), "
                "night (brightness down, DND on, silent), movie (max brightness/volume, DND on), "
                "meeting (DND on, vibrate, muted), driving (max volume/brightness, Maps open), "
                "gym (max volume, DND on, music play), reading (50% brightness, DND on, silent)."
            ),
            risk_level="safe",
            parameters={"routine": ToolParameter(type="string", description="Routine name", enum=["morning", "night", "movie", "meeting", "driving", "gym", "reading"])}
        ))
        self.register(ToolDefinition(
            name="set_alarm",
            description="Set an alarm at a specified hour (24h clock).",
            risk_level="safe",
            parameters={"hour": ToolParameter(type="integer", description="Hour in 24h format (0-23)"), "minute": ToolParameter(type="integer", description="Minute (0-59)", required=False)}
        ))
        self.register(ToolDefinition(
            name="set_timer",
            description="Start a countdown timer for a specified number of seconds.",
            risk_level="safe",
            parameters={"seconds": ToolParameter(type="integer", description="Duration in seconds")}
        ))
        self.register(ToolDefinition(
            name="set_reminder",
            description="Set a reminder to fire after a specified number of minutes.",
            risk_level="safe",
            parameters={"delay_minutes": ToolParameter(type="integer", description="Minutes until reminder fires"), "message": ToolParameter(type="string", description="Reminder message")}
        ))
        self.register(ToolDefinition(
            name="get_location",
            description="Get the device's current coarse location description.",
            risk_level="safe",
            permissions=["ACCESS_COARSE_LOCATION"]
        ))
        self.register(ToolDefinition(
            name="navigate_to",
            description="Open navigation directions to a specified place.",
            risk_level="safe",
            parameters={"place": ToolParameter(type="string", description="Destination place name or address")}
        ))
        self.register(ToolDefinition(
            name="read_calendar",
            description="Read upcoming calendar events for today.",
            risk_level="safe",
            permissions=["READ_CALENDAR"]
        ))
        self.register(ToolDefinition(
            name="get_daily_briefing",
            description="Get a full morning briefing: time, battery, storage, and calendar events.",
            risk_level="safe"
        ))
        self.register(ToolDefinition(
            name="lock_screen",
            description="Lock the device screen immediately.",
            risk_level="safe"
        ))


tool_registry = ToolRegistry()

