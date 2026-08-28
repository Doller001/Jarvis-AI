"""
Task Planner and Risk Policy for Jarvis Multi-Action Execution Engine.
Now uses canonical actions and capability policy.
"""

import re
from typing import Any
import uuid

from app.agent.execution_models import (
    ActionVerification,
    ExecutionPlan,
    PlannedAction,
)
from app.agent.intent_resolver import intent_resolver
from app.security.capability import CapabilityDecision, capability_manager
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
    """Decomposes user utterances into deterministic, dependency-aware ExecutionPlans."""

    def plan_utterance(
        self,
        utterance: str,
        session_id: str = "default-session",
        request_id: str | None = None
    ) -> ExecutionPlan:
        req_id = request_id or f"req-{uuid.uuid4().hex[:8]}"
        text = utterance.lower().strip()

        # Check for multi-action conjunction markers: "and", "aur", "then", "karke"
        has_multi = bool(re.search(r"\b(and|aur|then|karke|kholo aur|open and)\b", text))

        actions: list[PlannedAction] = []

        if has_multi:
            # Pattern 1: "open youtube and play <song>"
            if ("youtube" in text or "yt" in text) and ("play" in text or "chalao" in text or "bajao" in text or "search" in text):
                second_part = re.split(r"\b(and|aur|then)\b", text)[-1].strip()
                query = re.sub(r"^(open|kholo|play|search|chalao|bajao)\s+", "", second_part)
                query = re.sub(r"\b(youtube|yt|pe|par|mein|song|gaana|gana|video)\b", "", query).strip()
                query = query or "trending music"

                cmd1_id = f"cmd-{uuid.uuid4().hex[:6]}"
                cmd2_id = f"cmd-{uuid.uuid4().hex[:6]}"

                actions.append(
                    PlannedAction(
                        id=cmd1_id,
                        tool="open_app",
                        parameters={"app_name": "youtube"},
                        verification=ActionVerification(
                            type="foreground_app",
                            expected={"package": "com.google.android.youtube"}
                        )
                    )
                )
                actions.append(
                    PlannedAction(
                        id=cmd2_id,
                        tool="play_media_search",
                        parameters={"query": query, "app": "youtube"},
                        depends_on=[cmd1_id],
                        verification=ActionVerification(
                            type="media_playing",
                            expected={"app": "youtube"}
                        )
                    )
                )

            # Pattern 2: "camera kholo aur selfie lo" / "open camera and take selfie"
            elif "camera" in text and ("selfie" in text or "photo" in text):
                cmd1_id = f"cmd-{uuid.uuid4().hex[:6]}"
                cmd2_id = f"cmd-{uuid.uuid4().hex[:6]}"
                actions.append(
                    PlannedAction(
                        id=cmd1_id,
                        tool="open_app",
                        parameters={"app_name": "camera"},
                        verification=ActionVerification(type="foreground_app", expected={"package": "camera"})
                    )
                )
                actions.append(
                    PlannedAction(
                        id=cmd2_id,
                        tool="take_selfie",
                        parameters={},
                        depends_on=[cmd1_id],
                        verification=ActionVerification(type="photo_captured")
                    )
                )

            # Pattern 3: "torch on and volume 80"
            elif ("torch" in text or "flashlight" in text) and ("volume" in text or "awaz" in text):
                cmd1_id = f"cmd-{uuid.uuid4().hex[:6]}"
                cmd2_id = f"cmd-{uuid.uuid4().hex[:6]}"
                torch_state = "off" if "off" in text or "band" in text else "on"
                vol_match = re.search(r"(?:volume|awaz)\s+(\d+)", text)
                vol_level = int(vol_match.group(1)) if vol_match else 80

                actions.append(
                    PlannedAction(
                        id=cmd1_id,
                        tool="toggle_torch",
                        parameters={"state": torch_state},
                        verification=ActionVerification(type="torch_state", expected={"state": torch_state})
                    )
                )
                actions.append(
                    PlannedAction(
                        id=cmd2_id,
                        tool="set_volume",
                        parameters={"level": vol_level},
                        depends_on=[cmd1_id],
                        verification=ActionVerification(type="volume_level", expected={"level": vol_level})
                    )
                )

        # Level 1 Single-Action Intent Resolution
        if not actions:
            resolved = intent_resolver.resolve(utterance)
            if resolved:
                cmd_id = f"cmd-{uuid.uuid4().hex[:6]}"

                # Check capability policy
                capability = capability_manager.check_capability(resolved.intent)
                requires_confirmation = (
                    resolved.requires_confirmation
                    or not risk_policy.is_auto_executable(resolved.intent)
                    or capability.decision == CapabilityDecision.CONFIRM
                )

                actions.append(
                    PlannedAction(
                        id=cmd_id,
                        tool=resolved.intent,
                        parameters=resolved.entities,
                        requires_confirmation=requires_confirmation
                    )
                )

        return ExecutionPlan(
            request_id=req_id,
            session_id=session_id,
            utterance=utterance,
            actions=actions
        )


risk_policy = RiskPolicy()
task_planner = TaskPlanner()
