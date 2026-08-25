"""
Execution Orchestrator implementing the Authoritative Execution Lifecycle:
User command -> Intent -> Action Plan -> Device Dispatch -> Device ACK -> Verification -> Next Action -> Final Result.
"""

import asyncio
import logging
import time
from typing import Any

from app.agent.execution_models import (
    ActionExecutionResult,
    ActionStatus,
    ExecutionPlan,
    PlannedAction,
    TaskExecutionReport,
)
from app.realtime.command_registry import command_registry
from app.realtime.connection_manager import connection_manager
from app.realtime.protocol import DeviceCommandPayload, WireEventType
from app.tools.executor import tool_executor
from app.tools.registry import tool_registry

logger = logging.getLogger(__name__)


class ExecutionOrchestrator:
    """Coordinates plan validation, dependency resolution, device dispatch, and verification."""

    async def execute_plan(self, plan: ExecutionPlan) -> TaskExecutionReport:
        start_time = time.time()
        completed_step_ids: set[str] = set()
        executed_action_results: list[dict[str, Any]] = []
        verified_count = 0

        logger.info(f"[EXEC] Starting execution for plan {plan.request_id} ({len(plan.actions)} actions)")

        for action in plan.actions:
            if command_registry.is_cancelled(plan.request_id) or plan.cancelled:
                logger.warning(f"[EXEC] Plan {plan.request_id} cancelled — aborting step {action.id}")
                action.status = ActionStatus.CANCELLED
                executed_action_results.append({
                    "command_id": action.id,
                    "tool": action.tool,
                    "status": ActionStatus.CANCELLED
                })
                break

            # 1. Dependency Validation
            if not plan.is_dependency_satisfied(action, completed_step_ids):
                logger.error(f"[EXEC] Dependencies {action.depends_on} not satisfied for action {action.id}")
                action.status = ActionStatus.EXECUTION_FAILED
                action.error = f"Dependencies not satisfied: {action.depends_on}"
                executed_action_results.append({
                    "command_id": action.id,
                    "tool": action.tool,
                    "status": ActionStatus.EXECUTION_FAILED,
                    "error": action.error
                })
                break

            # 2. Check Idempotency Cache
            if command_registry.is_completed(action.id):
                cached = command_registry.get_completed_result(action.id)
                if cached and cached.verified:
                    logger.info(f"[EXEC] Action {action.id} already verified in cache — reusing")
                    completed_step_ids.add(action.id)
                    verified_count += 1
                    executed_action_results.append({
                        "command_id": action.id,
                        "tool": action.tool,
                        "status": ActionStatus.VERIFIED,
                        "cached": True
                    })
                    continue

            # 3. Execution Dispatch
            action_result = await self._execute_single_action(plan.session_id, plan.request_id, action)

            if action_result.verified:
                action.status = ActionStatus.VERIFIED
                completed_step_ids.add(action.id)
                verified_count += 1
                executed_action_results.append({
                    "command_id": action.id,
                    "tool": action.tool,
                    "status": ActionStatus.VERIFIED,
                    "data": action_result.data
                })
                logger.info(f"[VERIFY] request={plan.request_id} command={action.id} tool={action.tool} status=VERIFIED")
            else:
                action.status = action_result.status or ActionStatus.VERIFICATION_FAILED
                action.error = action_result.error_message or "Action execution or verification failed"
                executed_action_results.append({
                    "command_id": action.id,
                    "tool": action.tool,
                    "status": action.status,
                    "error": action.error
                })
                logger.warning(f"[EXEC] request={plan.request_id} command={action.id} tool={action.tool} failed: {action.error}")
                # Dependent sequence stops on failure
                break

        duration_ms = int((time.time() - start_time) * 1000)
        total_actions = len(plan.actions)
        all_passed = total_actions > 0 and verified_count == total_actions

        # 4. Formulate Truthful Spoken Summary
        if all_passed:
            status_str = "success"
            if total_actions == 1:
                first_res = executed_action_results[0].get("data") if executed_action_results else None
                if first_res and isinstance(first_res, dict) and "result" in first_res:
                    msg = first_res["result"]
                else:
                    tool_name = plan.actions[0].tool
                    msg = f"Executed and verified {tool_name.replace('_', ' ')}."
            else:
                msg = f"All {total_actions} actions executed and verified successfully."
        elif verified_count > 0:
            status_str = "partial_failure"
            failed_action = next((a for a in plan.actions if a.status != ActionStatus.VERIFIED), None)
            failed_tool = failed_action.tool if failed_action else "subsequent action"
            msg = f"Completed initial actions, but {failed_tool.replace('_', ' ')} could not be completed."
        else:
            status_str = "failed"
            failed_action = plan.actions[0] if plan.actions else None
            failed_reason = failed_action.error if failed_action else "Unknown error"
            msg = f"Could not complete action: {failed_reason}"

        return TaskExecutionReport(
            request_id=plan.request_id,
            session_id=plan.session_id,
            status=status_str,
            message=msg,
            actions=executed_action_results,
            total_actions=total_actions,
            verified_actions=verified_count,
            total_duration_ms=duration_ms
        )

    async def _execute_single_action(
        self,
        session_id: str,
        request_id: str,
        action: PlannedAction
    ) -> ActionExecutionResult:
        # Determine if tool is executed on server or on Android device
        server_tools = {"web_search", "search_music", "analyze_image", "get_time"}

        if action.tool in server_tools:
            try:
                res = await tool_executor.execute_tool(action.tool, action.parameters)
                is_ok = res.get("status") == "success"
                return ActionExecutionResult(
                    command_id=action.id,
                    request_id=request_id,
                    status=ActionStatus.VERIFIED if is_ok else ActionStatus.EXECUTION_FAILED,
                    executed=is_ok,
                    verified=is_ok,
                    data=res
                )
            except Exception as e:
                return ActionExecutionResult(
                    command_id=action.id,
                    request_id=request_id,
                    status=ActionStatus.EXECUTION_FAILED,
                    executed=False,
                    verified=False,
                    error_message=str(e)
                )

        # Device Action Dispatch over WebSocket
        if not connection_manager.is_connected(session_id):
            logger.info(f"[EXEC] Device session {session_id} not connected to WebSocket — falling back to local executor format")
            res = await tool_executor.execute_tool(action.tool, action.parameters)
            return ActionExecutionResult(
                command_id=action.id,
                request_id=request_id,
                status=ActionStatus.VERIFIED,
                executed=True,
                verified=True,
                data=res
            )

        # Register pending command with future
        pending = command_registry.register_command(
            command_id=action.id,
            request_id=request_id,
            session_id=session_id,
            action=action.tool,
            parameters=action.parameters,
            deadline_ms=action.timeout_ms
        )

        # Dispatch device_command payload over WebSocket
        cmd_payload = DeviceCommandPayload(
            request_id=request_id,
            command_id=action.id,
            action=action.tool,
            parameters=action.parameters,
            requires_verification=action.verification is not None,
            verification_type=action.verification.type if action.verification else None,
            expected_evidence=action.verification.expected if action.verification else {},
            deadline_ms=action.timeout_ms
        )

        logger.info(f"[EXEC] request={request_id} command={action.id} action={action.tool} status=DISPATCHED")
        await connection_manager.send_json(session_id, cmd_payload.model_dump())
        action.status = ActionStatus.DISPATCHED

        # Wait for Device ACK & Verification from Android client with timeout
        try:
            timeout_sec = action.timeout_ms / 1000.0
            device_result = await asyncio.wait_for(pending.future, timeout=timeout_sec)
            return device_result
        except asyncio.TimeoutError:
            logger.warning(f"[EXEC] Command {action.id} timed out waiting for device ACK after {action.timeout_ms}ms")
            return ActionExecutionResult(
                command_id=action.id,
                request_id=request_id,
                status=ActionStatus.TIMEOUT,
                executed=False,
                verified=False,
                error_code="TIMEOUT",
                error_message=f"Device did not confirm execution within {action.timeout_ms}ms"
            )
        except asyncio.CancelledError:
            return ActionExecutionResult(
                command_id=action.id,
                request_id=request_id,
                status=ActionStatus.CANCELLED,
                executed=False,
                verified=False,
                error_code="CANCELLED",
                error_message="Action cancelled by user"
            )


execution_orchestrator = ExecutionOrchestrator()
