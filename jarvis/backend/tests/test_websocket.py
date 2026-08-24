"""Tests for Jarvis WebSocket endpoint."""

import pytest
from fastapi import WebSocketDisconnect

from app.realtime.connection_manager import ConnectionManager
from app.realtime.ws import websocket_endpoint


class FakeWebSocket:
    def __init__(self, messages):
        self.messages = iter(messages)
        self.sent = []

    async def accept(self):
        return None

    async def send_json(self, data):
        self.sent.append(data)

    async def receive_json(self):
        try:
            return next(self.messages)
        except StopIteration as exc:
            raise WebSocketDisconnect() from exc


@pytest.mark.asyncio
async def test_ws_handshake_and_intent_execution():
    websocket = FakeWebSocket([
        {
            "type": "command",
            "request_id": "req-ws-1",
            "session_id": "jarvis-test-session",
            "text": "Jarvis time kya hai"
        }
    ])

    await websocket_endpoint(websocket, session_id="jarvis-test-session")

    assert websocket.sent[0]["type"] == "session_ready"
    assert websocket.sent[0]["assistant_name"] == "Jarvis"
    assert websocket.sent[1]["type"] == "command_result"
    assert websocket.sent[1]["action"] == "get_time"


def test_stale_socket_cannot_disconnect_replacement_session():
    manager = ConnectionManager()
    first, second = object(), object()
    manager.active_connections["shared-session"] = second

    manager.disconnect("shared-session", first)
    assert manager.active_connections["shared-session"] is second

    manager.disconnect("shared-session", second)
    assert "shared-session" not in manager.active_connections
