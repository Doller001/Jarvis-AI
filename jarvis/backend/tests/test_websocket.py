"""
Tests for Jarvis WebSocket endpoint.
"""

from fastapi.testclient import TestClient

from app.main import app
from app.realtime.connection_manager import ConnectionManager


def test_ws_handshake_and_intent_execution():
    client = TestClient(app)
    with client.websocket_connect("/ws?session_id=jarvis-test-session") as websocket:
        ready_event = websocket.receive_json()
        assert ready_event["type"] == "session_ready"
        assert ready_event["assistant_name"] == "Jarvis"

        websocket.send_json({
            "type": "command",
            "request_id": "req-ws-1",
            "session_id": "jarvis-test-session",
            "text": "Jarvis time kya hai"
        })

        res = websocket.receive_json()
        assert res["type"] == "command_result"
        assert res["action"] == "get_time"


def test_stale_socket_cannot_disconnect_replacement_session():
    manager = ConnectionManager()
    first, second = object(), object()
    manager.active_connections["shared-session"] = second

    manager.disconnect("shared-session", first)
    assert manager.active_connections["shared-session"] is second

    manager.disconnect("shared-session", second)
    assert "shared-session" not in manager.active_connections
