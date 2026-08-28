import json
import logging
import uuid

from fastapi import APIRouter, WebSocket, WebSocketDisconnect

from app.realtime.connection_manager import connection_manager
from app.realtime.message_router import message_router
from app.security.jwt_manager import jwt_manager
from app.security.device_registry import device_registry

logger = logging.getLogger(__name__)

ws_router = APIRouter()


@ws_router.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket, token: str | None = None, session_id: str | None = None):
    # Authenticate via JWT token passed as query parameter
    if token:
        payload = jwt_manager.validate_token(token)
        if payload is None:
            await websocket.close(code=4008, reason="Invalid or expired token")
            return
        device_id = payload.sub
        # Verify device is registered
        device = device_registry.get_device(device_id)
        if device is None:
            await websocket.close(code=4008, reason="Unknown device")
            return
        device_registry.touch_device(device_id)
    else:
        device_id = None

    # Assign distinct session ID per device/client if none or generic is provided
    effective_session_id = session_id if (session_id and session_id != "default-session") else f"sess-{uuid.uuid4().hex[:12]}"

    await connection_manager.connect(effective_session_id, websocket)

    await connection_manager.send_json(effective_session_id, {
        "type": "session_ready",
        "session_id": effective_session_id,
        "device_id": device_id,
        "status": "connected",
        "assistant_name": "Jarvis"
    })

    try:
        while True:
            try:
                data = await websocket.receive_json()
            except (json.JSONDecodeError, ValueError) as json_err:
                logger.warning(f"Malformed or non-JSON WebSocket frame received from {effective_session_id}: {json_err}")
                continue
            await message_router.route_message(effective_session_id, data, device_id=device_id)
    except WebSocketDisconnect:
        connection_manager.disconnect(effective_session_id, websocket)
    except Exception as e:
        logger.error(f"Error handling WebSocket session {effective_session_id}: {e}")
        connection_manager.disconnect(effective_session_id, websocket)
