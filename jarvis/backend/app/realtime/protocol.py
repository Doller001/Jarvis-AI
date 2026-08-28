"""
Realtime WebSocket protocol definitions for Jarvis with explicit Device Command & Result contracts.
"""

from typing import Any
from pydantic import BaseModel, Field

from app.realtime.canonical_protocol import CanonicalAction, RetryConfig, VerificationConfig


class WireEventType:
    CONNECT = "connect"
    COMMAND = "command"
    CONFIRMATION = "confirmation"
    CONFIRMATION_REQUEST = "confirmation_request"
    DEVICE_COMMAND = "device_command"
    CANONICAL_ACTION = "canonical_action"
    ACTION_GRAPH = "action_graph"
    DEVICE_RESULT = "device_result"
    CANCEL_REQUEST = "cancel_request"
    CANCEL_RESULT = "cancel_result"
    EXECUTION_REPORT = "execution_report"
    ACTION_RESULT = "action_result"
    TOKEN_STREAM = "token_stream"
    ERROR = "error"
    PING = "ping"
    PONG = "pong"


class ClientCommandPayload(BaseModel):
    type: str = WireEventType.COMMAND
    request_id: str
    session_id: str = "default-session"
    text: str


class ClientConfirmationPayload(BaseModel):
    type: str = WireEventType.CONFIRMATION
    request_id: str
    session_id: str = "default-session"
    confirmation_token: str
    confirmed: bool


class DeviceCommandPayload(BaseModel):
    type: str = WireEventType.DEVICE_COMMAND
    request_id: str
    command_id: str
    action: str
    parameters: dict[str, Any] = Field(default_factory=dict)
    requires_verification: bool = True
    verification_type: str | None = None
    expected_evidence: dict[str, Any] = Field(default_factory=dict)
    deadline_ms: int = 10000


class DeviceResultPayload(BaseModel):
    type: str = WireEventType.DEVICE_RESULT
    request_id: str
    command_id: str
    status: str  # "executed", "failed", "cancelled"
    verified: bool = False
    data: dict[str, Any] = Field(default_factory=dict)
    error_code: str | None = None
    error_message: str | None = None
    latency_ms: int = 0


class CancelRequestPayload(BaseModel):
    type: str = WireEventType.CANCEL_REQUEST
    request_id: str
    session_id: str = "default-session"
    reason: str = "User cancelled"


class ServerErrorPayload(BaseModel):
    type: str = WireEventType.ERROR
    request_id: str | None = None
    code: str
    message: str


class ActionGraphPayload(BaseModel):
    """Action graph sent from backend to Android for execution."""
    type: str = WireEventType.ACTION_GRAPH
    request_id: str
    actions: list[CanonicalAction]
    metadata: dict[str, Any] = Field(default_factory=dict)

    def model_dump(self, **kwargs) -> dict[str, Any]:
        base = super().model_dump(**kwargs)
        # Ensure canonical actions are properly serialized
        base["actions"] = [a.model_dump() for a in self.actions]
        return base
