"""
Capability Policy for Jarvis AI Backend.
Categorizes actions and enforces permission policy before execution.
"""

import logging
from enum import Enum
from dataclasses import dataclass

logger = logging.getLogger("jarvis.security.capability")


class CapabilityTier(str, Enum):
    """Risk-based capability tiers."""
    SAFE = "safe"                    # Read-only, no side effects
    DEVICE_CONTROL = "device_control"  # Modifies device state
    AUTOMATION = "automation"         # Accessibility-based UI automation
    HIGH_RISK = "high_risk"          # Irreversible or sensitive actions


class CapabilityDecision(str, Enum):
    """Result of capability check."""
    ALLOW = "allow"
    CONFIRM = "confirm"
    REJECT = "reject"


@dataclass
class CapabilityPolicy:
    """Defines the policy for a capability tier."""
    tier: CapabilityTier
    decision: CapabilityDecision
    requires_accessibility: bool = False
    requires_confirmation: bool = False
    requires_user_consent: bool = False
    reason: str = ""


# Default policies per tier
DEFAULT_POLICIES: dict[CapabilityTier, CapabilityPolicy] = {
    CapabilityTier.SAFE: CapabilityPolicy(
        tier=CapabilityTier.SAFE,
        decision=CapabilityDecision.ALLOW,
    ),
    CapabilityTier.DEVICE_CONTROL: CapabilityPolicy(
        tier=CapabilityTier.DEVICE_CONTROL,
        decision=CapabilityDecision.ALLOW,
        requires_user_consent=True,
    ),
    CapabilityTier.AUTOMATION: CapabilityPolicy(
        tier=CapabilityTier.AUTOMATION,
        decision=CapabilityDecision.ALLOW,
        requires_accessibility=True,
    ),
    CapabilityTier.HIGH_RISK: CapabilityPolicy(
        tier=CapabilityTier.HIGH_RISK,
        decision=CapabilityDecision.CONFIRM,
        requires_confirmation=True,
        requires_user_consent=True,
    ),
}

# Tool -> CapabilityTier mapping
TOOL_CAPABILITY_MAP: dict[str, CapabilityTier] = {
    # SAFE - Read-only operations
    "get_time": CapabilityTier.SAFE,
    "get_battery_level": CapabilityTier.SAFE,
    "read_screen": CapabilityTier.SAFE,
    "read_messages": CapabilityTier.SAFE,
    "read_call_log": CapabilityTier.SAFE,
    "read_calendar": CapabilityTier.SAFE,
    "get_location": CapabilityTier.SAFE,
    "get_daily_briefing": CapabilityTier.SAFE,
    "web_search": CapabilityTier.SAFE,
    "search_music": CapabilityTier.SAFE,
    "analyze_image": CapabilityTier.SAFE,

    # DEVICE_CONTROL - Modifies device state
    "toggle_torch": CapabilityTier.DEVICE_CONTROL,
    "toggle_wifi": CapabilityTier.DEVICE_CONTROL,
    "toggle_bluetooth": CapabilityTier.DEVICE_CONTROL,
    "set_volume": CapabilityTier.DEVICE_CONTROL,
    "set_brightness": CapabilityTier.DEVICE_CONTROL,
    "toggle_dnd": CapabilityTier.DEVICE_CONTROL,
    "set_ringer_mode": CapabilityTier.DEVICE_CONTROL,
    "toggle_rotation_lock": CapabilityTier.DEVICE_CONTROL,
    "lock_screen": CapabilityTier.DEVICE_CONTROL,
    "take_screenshot": CapabilityTier.DEVICE_CONTROL,
    "open_app": CapabilityTier.DEVICE_CONTROL,
    "close_app": CapabilityTier.DEVICE_CONTROL,
    "set_alarm": CapabilityTier.DEVICE_CONTROL,
    "set_timer": CapabilityTier.DEVICE_CONTROL,
    "set_reminder": CapabilityTier.DEVICE_CONTROL,
    "navigate_to": CapabilityTier.DEVICE_CONTROL,
    "run_routine": CapabilityTier.DEVICE_CONTROL,

    # AUTOMATION - UI automation via accessibility
    "click_element": CapabilityTier.AUTOMATION,
    "type_text": CapabilityTier.AUTOMATION,
    "swipe": CapabilityTier.AUTOMATION,
    "scroll": CapabilityTier.AUTOMATION,
    "search_text": CapabilityTier.AUTOMATION,
    "play_media": CapabilityTier.AUTOMATION,
    "pause_media": CapabilityTier.AUTOMATION,
    "take_selfie": CapabilityTier.AUTOMATION,
    "send_message": CapabilityTier.AUTOMATION,

    # HIGH_RISK - Irreversible or sensitive
    "call_contact": CapabilityTier.HIGH_RISK,
    "make_call": CapabilityTier.HIGH_RISK,
    "send_sms": CapabilityTier.HIGH_RISK,
    "whatsapp_send": CapabilityTier.HIGH_RISK,
    "delete_file": CapabilityTier.HIGH_RISK,
    "install_apk": CapabilityTier.HIGH_RISK,
    "change_security_settings": CapabilityTier.HIGH_RISK,
}


class CapabilityManager:
    """Enforces capability policy before tool execution."""

    def __init__(self):
        self._policies = dict(DEFAULT_POLICIES)
        self._overrides: dict[str, CapabilityTier] = {}

    def get_tier(self, tool_name: str) -> CapabilityTier:
        if tool_name in self._overrides:
            return self._overrides[tool_name]
        return TOOL_CAPABILITY_MAP.get(tool_name, CapabilityTier.SAFE)

    def check_capability(
        self,
        tool_name: str,
        accessibility_enabled: bool = True,
        user_consent: bool = False,
    ) -> CapabilityPolicy:
        tier = self.get_tier(tool_name)
        policy = self._policies[tier]

        # Check accessibility requirement
        if policy.requires_accessibility and not accessibility_enabled:
            return CapabilityPolicy(
                tier=tier,
                decision=CapabilityDecision.REJECT,
                requires_accessibility=True,
                reason=f"Accessibility service required for '{tool_name}'",
            )

        # Check confirmation requirement
        if policy.requires_confirmation and not user_consent:
            return CapabilityPolicy(
                tier=tier,
                decision=CapabilityDecision.CONFIRM,
                requires_confirmation=True,
                reason=f"Action '{tool_name}' requires user confirmation",
            )

        return policy

    def is_auto_executable(self, tool_name: str) -> bool:
        policy = self.check_capability(tool_name)
        return policy.decision == CapabilityDecision.ALLOW

    def requires_confirmation(self, tool_name: str) -> bool:
        policy = self.check_capability(tool_name)
        return policy.decision == CapabilityDecision.CONFIRM

    def is_rejected(self, tool_name: str) -> bool:
        policy = self.check_capability(tool_name)
        return policy.decision == CapabilityDecision.REJECT

    def list_tools_by_tier(self) -> dict[str, list[str]]:
        result: dict[str, list[str]] = {tier.value: [] for tier in CapabilityTier}
        for tool, tier in TOOL_CAPABILITY_MAP.items():
            result[tier.value].append(tool)
        return result


capability_manager = CapabilityManager()
