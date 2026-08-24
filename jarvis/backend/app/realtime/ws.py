"""
FastAPI WebSocket endpoint for Jarvis clients.
"""

import logging

from fastapi import APIRouter, WebSocket, WebSocketDisconnect

from app.realtime.connection_manager import connection_manager
from app.realtime.message_router import message_router
from app.security.auth import validate_ws_token

logger = logging.getLogger(__name__)

ws_router = APIRouter()


@ws_router.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket, token: str | None = None, session_id: str = "default-session"):
    if not validate_ws_token(token):
        await websocket.close(code=4008, reason="Unauthorized")
        return

    await connection_manager.connect(session_id, websocket)

    await connection_manager.send_json(session_id, {
        "type": "session_ready",
        "session_id": session_id,
        "status": "connected",
        "assistant_name": "Jarvis"
    })

    try:
        while True:
            data = await websocket.receive_json()
            await message_router.route_message(session_id, data)
    except WebSocketDisconnect:
        connection_manager.disconnect(session_id, websocket)
    except Exception as e:
        logger.error(f"Error handling WebSocket session {session_id}: {e}")
        connection_manager.disconnect(session_id, websocket)
