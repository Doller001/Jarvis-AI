"""
Fast Level-1 Deterministic Intent Resolver for Jarvis.
Resolves common device control commands without remote LLM overhead.
"""

from typing import Optional, Dict, Any
from pydantic import BaseModel, Field


class StructuredIntent(BaseModel):
    intent: str
    target: str = ""
    confidence: float = 1.0
    entities: Dict[str, Any] = Field(default_factory=dict)
    requires_confirmation: bool = False


class IntentResolver:
    def resolve(self, text: str) -> Optional[StructuredIntent]:
        t = text.lower().strip()

        # Strip optional wake phrase prefixes if passed to backend
        for prefix in ["jarvis", "hey jarvis", "hay jarvis", "jarvis suno", "jarvis listen"]:
            if t.startswith(prefix):
                t = t[len(prefix):].strip()

        # Level 1 — Time & Battery
        if t in ["time", "what time is it", "get time", "time kya hai", "current time"]:
            return StructuredIntent(intent="get_time", target="system", confidence=1.0)

        if t in ["battery", "battery level", "battery kitni hai", "get battery"]:
            return StructuredIntent(intent="get_battery_level", target="system", confidence=1.0)

        # Level 1 — Torch / Flashlight
        if "torch on" in t or "flashlight on" in t or "torch chalo" in t:
            return StructuredIntent(intent="toggle_torch", target="torch", confidence=0.99, entities={"state": "on"})
        if "torch off" in t or "flashlight off" in t or "torch band" in t:
            return StructuredIntent(intent="toggle_torch", target="torch", confidence=0.99, entities={"state": "off"})

        # Level 1 — Wi-Fi & Bluetooth
        if "wifi on" in t or "turn on wifi" in t or "wifi chalo" in t:
            return StructuredIntent(intent="toggle_wifi", target="wifi", confidence=0.98, entities={"state": "on"})
        if "wifi off" in t or "turn off wifi" in t or "wifi band" in t:
            return StructuredIntent(intent="toggle_wifi", target="wifi", confidence=0.98, entities={"state": "off"})

        if "bluetooth on" in t or "turn on bluetooth" in t:
            return StructuredIntent(intent="toggle_bluetooth", target="bluetooth", confidence=0.98, entities={"state": "on"})
        if "bluetooth off" in t or "turn off bluetooth" in t:
            return StructuredIntent(intent="toggle_bluetooth", target="bluetooth", confidence=0.98, entities={"state": "off"})

        # Level 1 — Volume
        if "volume up" in t or "volume badhao" in t:
            return StructuredIntent(intent="set_volume", target="volume", confidence=0.95, entities={"level": 80})
        if "volume down" in t or "volume kam karo" in t:
            return StructuredIntent(intent="set_volume", target="volume", confidence=0.95, entities={"level": 30})

        # Level 1 — App Launching
        if t.startswith("open ") or t.endswith(" kholo"):
            app_name = t.replace("open ", "").replace(" kholo", "").strip()
            return StructuredIntent(intent="open_app", target=app_name, confidence=0.96, entities={"app_name": app_name})

        # Level 1 — Accessibility Screen Reading
        if "read screen" in t or "screen padho" in t or "screen pe kya hai" in t:
            return StructuredIntent(intent="read_screen", target="accessibility", confidence=0.98)

        # Risky Actions requiring single-use confirmation token
        if t.startswith("call "):
            contact = t[5:].strip()
            return StructuredIntent(intent="call_contact", target=contact, confidence=0.95, entities={"contact_name": contact}, requires_confirmation=True)

        if t.startswith("whatsapp ") or "send whatsapp" in t:
            return StructuredIntent(intent="whatsapp_send", target="whatsapp", confidence=0.95, entities={"query": t}, requires_confirmation=True)

        if t.startswith("send sms") or t.startswith("sms "):
            return StructuredIntent(intent="send_sms", target="sms", confidence=0.95, entities={"query": t}, requires_confirmation=True)

        return None


intent_resolver = IntentResolver()
