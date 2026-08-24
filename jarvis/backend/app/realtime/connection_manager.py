"""
Jarvis Connection Manager handling WebSocket sessions.
"""

import logging
from typing import Any

from fastapi import WebSocket

logger = logging.getLogger(__name__)


class ConnectionManager:
    def __init__(self) -> None:
        self.active_connections: dict[str, WebSocket] = {}

    async def connect(self, session_id: str, websocket: WebSocket) -> None:
        await websocket.accept()
        self.active_connections[session_id] = websocket
        logger.info(f"Jarvis WebSocket session connected: {session_id}")

    def disconnect(self, session_id: str, websocket: WebSocket | None = None) -> None:
        active = self.active_connections.get(session_id)
        # A reconnect can replace a session's socket.  Do not let the stale
        # socket's finally block remove the newly connected client.
        if active is not None and (websocket is None or active is websocket):
            del self.active_connections[session_id]
            logger.info(f"Jarvis WebSocket session disconnected: {session_id}")

    async def send_json(self, session_id: str, data: dict[str, Any]) -> None:
        websocket = self.active_connections.get(session_id)
        if websocket:
            try:
                await websocket.send_json(data)
            except Exception as e:
                logger.warning(f"Failed to send JSON to WebSocket session {session_id}: {e}")
                self.disconnect(session_id)


connection_manager = ConnectionManager()
