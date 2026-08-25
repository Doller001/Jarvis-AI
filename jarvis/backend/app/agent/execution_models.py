"""
Execution Models for Jarvis Multi-Action Planning and Device Verification.
"""

from dataclasses import dataclass, field
import time
from typing import Any


class ActionStatus:
    CREATED = "CREATED"
    DISPATCHED = "DISPATCHED"
    EXECUTING = "EXECUTING"
    EXECUTED = "EXECUTED"
    VERIFIED = "VERIFIED"
    DISPATCH_FAILED = "DISPATCH_FAILED"
    EXECUTION_FAILED = "EXECUTION_FAILED"
    VERIFICATION_FAILED = "VERIFICATION_FAILED"
    TIMEOUT = "TIMEOUT"
    CANCELLED = "CANCELLED"


@dataclass
class ActionVerification:
    type: str  # e.g. "foreground_app", "torch_state", "volume_level", "media_playing", "none"
    expected: dict[str, Any] = field(default_factory=dict)
    verified: bool = False
    evidence: dict[str, Any] = field(default_factory=dict)
    reason: str | None = None


@dataclass
class PlannedAction:
    id: str
    tool: str
    parameters: dict[str, Any] = field(default_factory=dict)
    depends_on: list[str] = field(default_factory=list)
    verification: ActionVerification | None = None
    timeout_ms: int = 8000
    requires_confirmation: bool = False
    status: str = ActionStatus.CREATED
    error: str | None = None
    output: dict[str, Any] | None = None
    dispatched_at: float | None = None
    completed_at: float | None = None


@dataclass
class ExecutionPlan:
    request_id: str
    session_id: str
    utterance: str
    actions: list[PlannedAction] = field(default_factory=list)
    created_at: float = field(default_factory=time.time)
    completed: bool = False
    cancelled: bool = False

    def is_dependency_satisfied(self, action: PlannedAction, completed_action_ids: set[str]) -> bool:
        return all(dep_id in completed_action_ids for dep_id in action.depends_on)


@dataclass
class ActionExecutionResult:
    command_id: str
    request_id: str
    status: str
    executed: bool
    verified: bool
    data: dict[str, Any] = field(default_factory=dict)
    error_code: str | None = None
    error_message: str | None = None
    latency_ms: int = 0


@dataclass
class TaskExecutionReport:
    request_id: str
    session_id: str
    status: str  # "success", "partial_failure", "failed", "cancelled"
    message: str
    actions: list[dict[str, Any]] = field(default_factory=list)
    total_actions: int = 0
    verified_actions: int = 0
    total_duration_ms: int = 0
