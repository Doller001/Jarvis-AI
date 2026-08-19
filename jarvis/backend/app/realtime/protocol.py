"""
Realtime WebSocket protocol definitions for Jarvis.
"""

from typing import Dict, Any, Optional
from pydantic import BaseModel, Field


class WireEventType:
    CONNECT = "connect"
    COMMAND = "command"
    CONFIRMATION = "confirmation"
    CONFIRMATION_REQUEST = "confirmation_request"
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


class ServerErrorPayload(BaseModel):
    type: str = WireEventType.ERROR
    request_id: Optional[str] = None
    code: str
    message: str
