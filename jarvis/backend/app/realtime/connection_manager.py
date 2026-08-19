"""
Jarvis Connection Manager handling WebSocket sessions.
"""

import logging
from typing import Dict, Any
from fastapi import WebSocket

logger = logging.getLogger(__name__)


class ConnectionManager:
    def __init__(self) -> None:
        self.active_connections: Dict[str, WebSocket] = {}

    async def connect(self, session_id: str, websocket: WebSocket) -> None:
        await websocket.accept()
        self.active_connections[session_id] = websocket
        logger.info(f"Jarvis WebSocket session connected: {session_id}")

    def disconnect(self, session_id: str) -> None:
        if session_id in self.active_connections:
            del self.active_connections[session_id]
            logger.info(f"Jarvis WebSocket session disconnected: {session_id}")

    async def send_json(self, session_id: str, data: Dict[str, Any]) -> None:
        websocket = self.active_connections.get(session_id)
        if websocket:
            await websocket.send_json(data)


connection_manager = ConnectionManager()
