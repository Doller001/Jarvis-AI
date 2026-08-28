"""
Canonical Action Protocol for Jarvis.
Unified action model shared by Android and backend.
"""

from typing import Any
from pydantic import BaseModel, Field


class RetryConfig(BaseModel):
    max_attempts: int = 2
    backoff_ms: int = 500


class VerificationConfig(BaseModel):
    type: str = "none"  # device_state, app_foreground, media_playing, screen_contains, none
    expected: dict[str, Any] = Field(default_factory=dict)


class CanonicalAction(BaseModel):
    """
    Unified action model shared by Android and backend.
    This is the single source of truth for action representation.
    """
    id: str
    type: str  # e.g. "device.toggle_torch", "app.open", "automation.click"
    parameters: dict[str, Any] = Field(default_factory=dict)
    requires_confirmation: bool = False
    timeout_ms: int = 8000
    retry: RetryConfig = Field(default_factory=RetryConfig)
    verification: VerificationConfig = Field(default_factory=VerificationConfig)
    depends_on: list[str] = Field(default_factory=list)
    metadata: dict[str, Any] = Field(default_factory=dict)

    @property
    def namespace(self) -> str:
        """Extract namespace from type (e.g., 'device' from 'device.toggle_torch')."""
        return self.type.split(".")[0] if "." in self.type else ""

    @property
    def action_name(self) -> str:
        """Extract action name from type (e.g., 'toggle_torch' from 'device.toggle_torch')."""
        return self.type.split(".")[-1] if "." in self.type else self.type

    def is_device_action(self) -> bool:
        return self.namespace in ("device", "automation")

    def is_server_action(self) -> bool:
        return self.namespace == "server"

    def requires_accessibility(self) -> bool:
        return self.namespace == "automation"


class ActionGraph(BaseModel):
    """
    A graph of actions with dependencies.
    Backend generates this; Android executes it.
    """
    actions: list[CanonicalAction]
    metadata: dict[str, Any] = Field(default_factory=dict)

    def get_action(self, action_id: str) -> CanonicalAction | None:
        for action in self.actions:
            if action.id == action_id:
                return action
        return None

    def get_ready_actions(self, completed_ids: set[str]) -> list[CanonicalAction]:
        """Get actions whose dependencies are all satisfied."""
        return [
            a for a in self.actions
            if a.id not in completed_ids
            and all(dep in completed_ids for dep in a.depends_on)
        ]

    def is_complete(self, completed_ids: set[str]) -> bool:
        return all(a.id in completed_ids for a in self.actions)
