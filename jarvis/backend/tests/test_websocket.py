"""
Tests for Jarvis WebSocket endpoint.
"""

from fastapi.testclient import TestClient
from app.main import app


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
