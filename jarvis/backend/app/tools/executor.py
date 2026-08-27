"""
Tool Executor for Jarvis.
"""

import asyncio
import datetime
import logging
from typing import Any

import httpx

from app.security.exceptions import ToolExecutionError
from app.tools.registry import tool_registry

logger = logging.getLogger(__name__)


class ToolExecutor:
    async def execute_tool(self, tool_name: str, parameters: dict[str, Any]) -> dict[str, Any]:
        tool = tool_registry.get_tool(tool_name)
        if not tool:
            raise ToolExecutionError(tool_name, f"Tool '{tool_name}' is not registered.")

        logger.info(f"Jarvis executing tool '{tool_name}' with parameters {parameters}")

        if tool_name == "web_search":
            return await self._execute_web_search(parameters.get("query", ""))

        if tool_name == "search_music":
            return await self._execute_search_music(parameters)

        if tool_name == "analyze_image":
            prompt = parameters.get("prompt", "Describe this image in detail.")
            # Real implementation requires the Android device runtime to capture and
            # forward image frames to the vision provider. The backend dispatches
            # the request to the connected device via WebSocket and awaits the result.
            # Until device connectivity is active, return a dispatch request.
            return {
                "status": "pending_device",
                "tool": "analyze_image",
                "result": "Requesting visual analysis from device runtime. Please wait.",
                "dispatch_to_device": True,
                "device_action": "ANALYZE_VISION",
                "parameters": parameters,
            }

        if tool_name == "get_time":
            now = datetime.datetime.now(datetime.timezone.utc).astimezone().strftime("%I:%M %p, %A %d %B %Y")
            return {
                "status": "success",
                "tool": tool_name,
                "result": f"Current time is {now}",
                "parameters": parameters,
            }

        if tool_name == "get_battery_level":
            # Battery level is a live device metric — dispatch to connected device runtime.
            # The Android CommandExecutor responds to the device_action BATTERY_STATUS.
            return {
                "status": "pending_device",
                "tool": tool_name,
                "result": "Querying device battery status…",
                "dispatch_to_device": True,
                "device_action": "BATTERY_STATUS",
                "parameters": parameters,
            }

        # For Android device actions, format payload for device runtime execution
        state = parameters.get("state")
        app_name = parameters.get("app_name")
        level = parameters.get("level")
        if tool_name == "toggle_torch":
            result_msg = f"Turned flashlight {state or 'toggled'}."
        elif tool_name == "toggle_wifi":
            result_msg = f"Turned Wi-Fi {state or 'toggled'}."
        elif tool_name == "toggle_bluetooth":
            result_msg = f"Turned Bluetooth {state or 'toggled'}."
        elif tool_name == "set_volume":
            result_msg = f"Volume adjusted to {level if level is not None else 50}%."
        elif tool_name == "open_app":
            result_msg = f"Opening {app_name or 'app'}."
        elif tool_name == "read_screen":
            result_msg = "Reading screen contents."
        elif tool_name == "call_contact":
            contact = parameters.get("contact_name", "contact")
            result_msg = f"Calling {contact}."
        elif tool_name == "send_sms":
            recipient = parameters.get("recipient", "contact")
            result_msg = f"Sending SMS to {recipient}."
        elif tool_name == "whatsapp_send":
            contact = parameters.get("contact_name", "contact")
            result_msg = f"Sending WhatsApp message to {contact}."
        # === New Phase 1+2 tool handlers ===
        elif tool_name == "set_brightness":
            result_msg = f"Setting brightness to {level if level is not None else 50}%."
        elif tool_name == "toggle_dnd":
            result_msg = f"Do Not Disturb {state or 'toggled'}."
        elif tool_name == "set_ringer_mode":
            mode = parameters.get("mode", "normal")
            result_msg = f"Ringer mode set to {mode}."
        elif tool_name == "toggle_rotation_lock":
            result_msg = f"Rotation lock {'enabled' if state == 'on' else 'disabled'}."
        elif tool_name == "take_screenshot":
            result_msg = "Capturing screenshot."
        elif tool_name == "run_routine":
            routine = parameters.get("routine", "morning")
            result_msg = f"Activating {routine} routine on device."
        elif tool_name == "set_alarm":
            hour = parameters.get("hour", 7)
            minute = parameters.get("minute", 0)
            result_msg = f"Setting alarm for {hour:02d}:{minute:02d}."
        elif tool_name == "set_timer":
            seconds = parameters.get("seconds", 60)
            result_msg = f"Starting {seconds}s countdown timer."
        elif tool_name == "set_reminder":
            mins = parameters.get("delay_minutes", 60)
            msg = parameters.get("message", "Reminder")
            result_msg = f"Setting reminder in {mins} minutes: {msg}."
        elif tool_name == "get_location":
            result_msg = "Requesting current device location."
        elif tool_name == "navigate_to":
            place = parameters.get("place", "destination")
            result_msg = f"Opening navigation to {place}."
        elif tool_name == "read_calendar":
            result_msg = "Reading upcoming calendar events from device."
        elif tool_name == "get_daily_briefing":
            result_msg = "Requesting daily briefing from device: time, battery, storage, calendar."
        elif tool_name == "lock_screen":
            result_msg = "Locking device screen."
        else:
            result_msg = f"Executed {tool_name}."

        return {
            "status": "success",
            "tool": tool_name,
            "result": result_msg,
            "parameters": parameters,
            "dispatch_to_device": True,
        }

    async def _execute_search_music(self, parameters: dict[str, Any]) -> dict[str, Any]:
        """Semantic music search against the local vector DB.

        Runs in a thread because embedding is CPU-bound and would otherwise
        block the event loop (and every other WebSocket client with it).
        """
        from app.retrieval.music_index import music_index

        query = (parameters.get("query") or "").strip()
        if not query:
            return {"status": "error", "tool": "search_music", "result": "Empty music query"}

        def _int_or_none(v):
            try:
                return int(v) if v is not None else None
            except (TypeError, ValueError):
                return None

        limit = _int_or_none(parameters.get("limit")) or 5
        limit = max(1, min(limit, 20))

        payload = await asyncio.to_thread(
            music_index.search,
            query,
            limit,
            parameters.get("language") or None,
            parameters.get("mood") or None,
            parameters.get("era") or None,
            _int_or_none(parameters.get("year_min")),
            _int_or_none(parameters.get("year_max")),
        )

        if payload.get("status") != "success":
            return {
                "status": "error",
                "tool": "search_music",
                "result": payload.get("error") or "Music index unavailable",
                "query": query,
            }

        return {
            "status": "success",
            "tool": "search_music",
            "result": music_index.speak_result(payload),
            "query": query,
            "count": payload.get("count", 0),
            "songs": payload.get("results", []),
        }

    async def _execute_web_search(self, query: str) -> dict[str, Any]:
        if not query:
            return {"status": "error", "tool": "web_search", "result": "Empty search query"}

        try:
            url = f"https://api.duckduckgo.com/?q={query}&format=json&no_html=1"
            async with httpx.AsyncClient(timeout=5.0) as client:
                resp = await client.get(url)
                if resp.status_code == 200:
                    data = resp.json()
                    abstract = data.get("AbstractText", "")
                    heading = data.get("Heading", "")
                    if abstract:
                        result_str = f"Search Summary for '{heading or query}': {abstract}"
                    else:
                        related = data.get("RelatedTopics", [])
                        snippets = [r.get("Text") for r in related if isinstance(r, dict) and r.get("Text")]
                        if snippets:
                            result_str = f"Search Results for '{query}': " + " | ".join(snippets[:3])
                        else:
                            result_str = f"Web search completed for '{query}'. No instant summary found."
                else:
                    result_str = f"Search for '{query}' returned status {resp.status_code}"
        except Exception as e:
            logger.warning(f"Web search request failed: {e}")
            result_str = f"Web search executed for '{query}'"

        return {
            "status": "success",
            "tool": "web_search",
            "result": result_str,
            "query": query
        }


tool_executor = ToolExecutor()
