"""
Command Registry managing pending, executing, and completed device commands with idempotency and reconnect safety.
"""

import asyncio
from dataclasses import dataclass, field
import logging
import time
from typing import Any

from app.agent.execution_models import ActionExecutionResult, ActionStatus

logger = logging.getLogger(__name__)


@dataclass
class PendingCommand:
    command_id: str
    request_id: str
    session_id: str
    action: str
    parameters: dict[str, Any]
    status: str = ActionStatus.CREATED
    retry_count: int = 0
    created_at: float = field(default_factory=time.time)
    deadline_ms: int = 10000
    future: asyncio.Future | None = None


class CommandRegistry:
    def __init__(self):
        self._pending_commands: dict[str, PendingCommand] = {}
        self._completed_commands: dict[str, ActionExecutionResult] = {}
        self._cancelled_requests: set[str] = set()

    def register_command(
        self,
        command_id: str,
        request_id: str,
        session_id: str,
        action: str,
        parameters: dict[str, Any],
        deadline_ms: int = 10000
    ) -> PendingCommand:
        pending = PendingCommand(
            command_id=command_id,
            request_id=request_id,
            session_id=session_id,
            action=action,
            parameters=parameters,
            deadline_ms=deadline_ms,
            future=asyncio.get_event_loop().create_future()
        )
        self._pending_commands[command_id] = pending
        logger.info(f"[COMMAND_REGISTRY] Registered command {command_id} for request {request_id}")
        return pending

    def is_completed(self, command_id: str) -> bool:
        return command_id in self._completed_commands

    def get_completed_result(self, command_id: str) -> ActionExecutionResult | None:
        return self._completed_commands.get(command_id)

    def is_cancelled(self, request_id: str) -> bool:
        return request_id in self._cancelled_requests

    def record_result(self, result: ActionExecutionResult) -> None:
        cmd_id = result.command_id
        self._completed_commands[cmd_id] = result

        pending = self._pending_commands.pop(cmd_id, None)
        if pending and pending.future and not pending.future.done():
            pending.future.set_result(result)

        logger.info(f"[COMMAND_REGISTRY] Recorded result for {cmd_id}: status={result.status}, verified={result.verified}")

    def cancel_request(self, request_id: str) -> None:
        self._cancelled_requests.add(request_id)
        # Cancel any active pending commands belonging to this request
        for cmd_id, pending in list(self._pending_commands.items()):
            if pending.request_id == request_id:
                pending.status = ActionStatus.CANCELLED
                if pending.future and not pending.future.done():
                    pending.future.cancel()
                self._pending_commands.pop(cmd_id, None)
        logger.info(f"[COMMAND_REGISTRY] Cancelled request {request_id}")

    def reconcile_on_reconnect(self, session_id: str, last_completed_cmd_id: str | None = None) -> list[PendingCommand]:
        """Returns commands that are still pending execution for this session."""
        pending_list = []
        for cmd_id, cmd in list(self._pending_commands.items()):
            if cmd.session_id == session_id:
                if last_completed_cmd_id and cmd_id == last_completed_cmd_id:
                    self._pending_commands.pop(cmd_id, None)
                else:
                    pending_list.append(cmd)
        logger.info(f"[COMMAND_REGISTRY] Reconciled reconnect for session {session_id}: {len(pending_list)} pending commands")
        return pending_list


command_registry = CommandRegistry()
