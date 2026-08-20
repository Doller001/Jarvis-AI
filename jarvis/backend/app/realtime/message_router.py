"""
Jarvis Realtime Message Router linking transport directly to JarvisBrain.
"""

import logging
from typing import Dict, Any

from app.agent.orchestrator import jarvis_brain
from app.tools.executor import tool_executor
from app.security.token_manager import token_manager
from app.memory.memory_manager import memory_manager
from app.realtime.connection_manager import connection_manager
from app.realtime.protocol import (
    ClientCommandPayload,
    ClientConfirmationPayload,
    ServerErrorPayload,
    WireEventType,
)

logger = logging.getLogger(__name__)


class MessageRouter:
    async def route_message(self, session_id: str, raw_data: Dict[str, Any]) -> None:
        if not isinstance(raw_data, dict):
            return

        msg_type = raw_data.get("type")

        if msg_type == WireEventType.COMMAND:
            payload = ClientCommandPayload(**raw_data)
            brain_res = await jarvis_brain.process_utterance(
                text=payload.text,
                session_id=session_id,
                request_id=payload.request_id
            )
            await connection_manager.send_json(session_id, brain_res)

        elif msg_type == WireEventType.CONFIRMATION:
            payload = ClientConfirmationPayload(**raw_data)
            await self._handle_confirmation(session_id, payload)

        elif msg_type == WireEventType.PING:
            await connection_manager.send_json(session_id, {"type": WireEventType.PONG})

        else:
            err = ServerErrorPayload(
                request_id=raw_data.get("request_id"),
                code="UNKNOWN_MESSAGE_TYPE",
                message=f"Unsupported message type: {msg_type}"
            )
            await connection_manager.send_json(session_id, err.model_dump())

    async def _handle_confirmation(self, session_id: str, payload: ClientConfirmationPayload) -> None:
        if not payload.confirmed:
            err = ServerErrorPayload(
                request_id=payload.request_id,
                code="ACTION_CANCELLED",
                message="User declined confirmation request."
            )
            await connection_manager.send_json(session_id, err.model_dump())
            return

        valid_payload = token_manager.validate_and_consume(
            token_str=payload.confirmation_token,
            session_id=session_id
        )

        if not valid_payload:
            err = ServerErrorPayload(
                request_id=payload.request_id,
                code="INVALID_CONFIRMATION_TOKEN",
                message="Confirmation token is invalid, expired, or already used."
            )
            await connection_manager.send_json(session_id, err.model_dump())
            return

        exec_res = await tool_executor.execute_tool(valid_payload.action, valid_payload.parameters)
        response_text = exec_res.get("result") or f"Jarvis confirmed and executed '{valid_payload.action}'."
        memory_manager.record_assistant_message(session_id, response_text)

        res = {
            "type": WireEventType.ACTION_RESULT,
            "request_id": payload.request_id,
            "session_id": session_id,
            "action": valid_payload.action,
            "parameters": valid_payload.parameters,
            "response_text": response_text,
            "execution_result": exec_res
        }
        await connection_manager.send_json(session_id, res)


message_router = MessageRouter()
