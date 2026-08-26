"""
Jarvis Realtime Message Router handling commands, device ACKs/results, confirmations, and cancellations.
"""

import logging
from typing import Any

from app.agent.execution_models import ActionExecutionResult, ActionStatus
from app.agent.orchestrator import jarvis_brain
from app.memory.memory_manager import memory_manager
from app.realtime.command_registry import command_registry
from app.realtime.connection_manager import connection_manager
from app.realtime.protocol import (
    CancelRequestPayload,
    ClientCommandPayload,
    ClientConfirmationPayload,
    DeviceResultPayload,
    ServerErrorPayload,
    WireEventType,
)
from app.security.token_manager import token_manager
from app.tools.executor import tool_executor

logger = logging.getLogger(__name__)


class MessageRouter:
    async def route_message(self, session_id: str, raw_data: dict[str, Any]) -> None:
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

        elif msg_type == WireEventType.DEVICE_RESULT:
            # Device ACK & Verification result from Android client
            payload = DeviceResultPayload(**raw_data)
            result = ActionExecutionResult(
                command_id=payload.command_id,
                request_id=payload.request_id,
                status=ActionStatus.VERIFIED if payload.verified else (ActionStatus.EXECUTION_FAILED if payload.status == "failed" else ActionStatus.EXECUTED),
                executed=payload.status in ("executed", "success"),
                verified=payload.verified,
                data=payload.data,
                error_code=payload.error_code,
                error_message=payload.error_message,
                latency_ms=payload.latency_ms
            )
            command_registry.record_result(result)

        elif msg_type == WireEventType.CANCEL_REQUEST:
            payload = CancelRequestPayload(**raw_data)
            command_registry.cancel_request(payload.request_id)
            await connection_manager.send_json(session_id, {
                "type": WireEventType.CANCEL_RESULT,
                "request_id": payload.request_id,
                "status": "cancelled",
                "message": "Task execution cancelled by user."
            })

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
            session_id=session_id,
            request_id=payload.request_id,
        )

        if not valid_payload:
            err = ServerErrorPayload(
                request_id=payload.request_id,
                code="INVALID_CONFIRMATION_TOKEN",
                message="Confirmation token is invalid, expired, or already used."
            )
            await connection_manager.send_json(session_id, err.model_dump())
            return

        from app.agent.execution_models import ActionVerification, ExecutionPlan, PlannedAction
        from app.agent.execution_orchestrator import execution_orchestrator

        planned_action = PlannedAction(
            id=f"cmd-conf-{payload.request_id}",
            tool=valid_payload.action,
            parameters=valid_payload.parameters,
            verification=ActionVerification(type="device_ack")
        )
        plan = ExecutionPlan(
            request_id=payload.request_id,
            session_id=session_id,
            actions=[planned_action]
        )
        report = await execution_orchestrator.execute_plan(plan)
        response_text = report.message or f"Jarvis confirmed and executed '{valid_payload.action}'."
        memory_manager.record_assistant_message(session_id, response_text)

        res = {
            "type": WireEventType.ACTION_RESULT,
            "request_id": payload.request_id,
            "session_id": session_id,
            "action": valid_payload.action,
            "parameters": valid_payload.parameters,
            "response_text": response_text,
            "execution_result": {
                "status": report.status,
                "verified": report.verified_actions > 0,
                "actions": report.actions
            }
        }
        await connection_manager.send_json(session_id, res)


message_router = MessageRouter()
