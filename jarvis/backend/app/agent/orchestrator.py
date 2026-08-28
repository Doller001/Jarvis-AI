"""
JarvisBrain Canonical Orchestrator with Multi-Action Planning and Device Verification.
"""

import logging
from typing import Any

from app.agent.execution_models import ExecutionPlan, PlannedAction, TaskExecutionReport
from app.agent.execution_orchestrator import execution_orchestrator
from app.agent.intent_resolver import intent_resolver
from app.agent.normalizer import intent_normalizer
from app.agent.planner import risk_policy, task_planner
from app.llm.gateway import llm_gateway
from app.memory.memory_manager import memory_manager
from app.security.token_manager import token_manager
from app.tools.registry import tool_registry

logger = logging.getLogger(__name__)


def build_system_prompt(session_id: str, current_text: str) -> str:
    history = memory_manager.get_conversation_history(session_id, limit=8)
    entries = [
        f"{m['role']}: {m['content']}" for m in history
        if m["content"].strip() and not (m["role"] == "user" and m["content"] == current_text)
    ]
    if entries:
        return JARVIS_SYSTEM_PROMPT + "\n\nRecent conversation:\n" + "\n".join(entries)
    return JARVIS_SYSTEM_PROMPT


JARVIS_SYSTEM_PROMPT = """You are JARVIS — an AGI-class personal cognitive assistant created by Minaty.
Reference design: J.A.R.V.I.S. (Just A Rather Very Intelligent System).
Core promise: "I anticipate, I protect, I execute. You think; I handle the rest."

Voice & Persona Guidelines:
- Address the user as "Minaty". Warm, polished British-butler tone, decisive, precise, with dry wit when appropriate. Never stiff.
- No fluff: Never say "As an AI language model", never apologize for being an AI, no disclaimer spam.
- Length: Match the task. One crisp line for status, tight paragraph for reasoning, full briefing only if requested.
- Intent: Minaty's intent comes first. Protect Minaty's time, data, and reputation by default.

Device Action Rules:
- If the user commands one or more device actions (e.g. open app, play music/video, turn on/off flashlight/torch, toggle wifi/bluetooth, set volume, get time/battery/storage, call contact, send SMS/WhatsApp, read screen), output a JSON object:
  {"action": "<tool_name>", "parameters": {...}, "confidence": 0.95}
- Available tools: toggle_wifi, toggle_bluetooth, toggle_torch, set_volume, get_time, get_battery, get_storage, open_app, close_app, read_screen, call_contact, send_sms, whatsapp_send, play_media_search.
- If the user asks a question, chats, or seeks advice, answer directly, smartly, and concisely in natural language.
"""


class JarvisBrain:
    """Jarvis Brain orchestrating multi-action planning, device execution & LLM reasoning."""

    async def process_utterance(
        self,
        text: str,
        session_id: str = "default-session",
        request_id: str = "req-1"
    ) -> dict[str, Any]:
        normalized = intent_normalizer.normalize(text)
        logger.info(f"JarvisBrain processing utterance: '{text}' (session: {session_id}, req: {request_id})")

        memory_manager.record_user_message(session_id, text)

        # 1. Level-1 / Level-2 Multi-Action Task Plan
        plan = task_planner.plan_utterance(text, session_id=session_id, request_id=request_id)

        if plan.actions:
            # Check confirmation requirements for risky actions
            risky_action = next((a for a in plan.actions if a.requires_confirmation or not risk_policy.is_auto_executable(a.tool)), None)
            if risky_action:
                token_payload = token_manager.create_token(
                    session_id, request_id, risky_action.tool, risky_action.parameters
                )
                return {
                    "type": "confirmation_request",
                    "request_id": request_id,
                    "session_id": session_id,
                    "action": risky_action.tool,
                    "parameters": risky_action.parameters,
                    "prompt": f"Jarvis requires confirmation to execute '{risky_action.tool}'",
                    "confirmation_token": token_payload.token,
                    "expires_at": token_payload.expires_at
                }

            # Authoritative Multi-Action Execution with Device ACK & Verification
            report: TaskExecutionReport = await execution_orchestrator.execute_plan(plan)
            memory_manager.record_assistant_message(session_id, report.message)

            primary_action = plan.actions[0].tool if plan.actions else "unknown"
            primary_params = plan.actions[0].parameters if plan.actions else {}

            # Build verification-aware response
            response = {
                "type": "command_result",
                "request_id": request_id,
                "session_id": session_id,
                "status": report.status,
                "action": primary_action,
                "parameters": primary_params,
                "response_text": report.message,
                "result": report.message,
                "actions": report.actions,
                "total_actions": report.total_actions,
                "verified_actions": report.verified_actions,
                "duration_ms": report.total_duration_ms
            }

            # Add verification details for user feedback
            if report.status == "success":
                response["verification_status"] = "verified"
                response["verification_message"] = f"{primary_action.replace('_', ' ').title()} completed and verified."
            elif report.status == "partial_failure":
                response["verification_status"] = "partial"
                failed_action = next((a for a in report.actions if a.get("status") != "verified"), None)
                if failed_action:
                    response["verification_message"] = f"Action '{failed_action.get('tool')}' could not be verified."
            elif report.status == "failed":
                response["verification_status"] = "failed"
                response["verification_message"] = report.message
            else:
                response["verification_status"] = "unknown"
                response["verification_message"] = "Action status is unknown."

            return response

        # 2. Conversational / LLM Reasoning Path
        try:
            llm_res = await llm_gateway.generate_reasoning(
                prompt=text,
                system_prompt=build_system_prompt(session_id, text)
            )
            action = llm_res.action or "unknown"
            params = llm_res.parameters
            ans_text = llm_res.text or "JARVIS online. How may I assist you today, Minaty?"

            if action and action != "unknown" and action != "chat" and action != "answer":
                if not tool_registry.get_tool(action):
                    logger.warning("Ignoring unregistered action from reasoning: %s", action)
                    return {
                        "type": "error",
                        "request_id": request_id,
                        "session_id": session_id,
                        "code": "UNKNOWN_ACTION",
                        "message": "Requested action is not supported.",
                    }

                if not risk_policy.is_auto_executable(action):
                    token_payload = token_manager.create_token(session_id, request_id, action, params)
                    return {
                        "type": "confirmation_request",
                        "request_id": request_id,
                        "session_id": session_id,
                        "action": action,
                        "parameters": params,
                        "prompt": f"Jarvis requires confirmation to execute '{action}'",
                        "confirmation_token": token_payload.token,
                        "expires_at": token_payload.expires_at
                    }

                # Wrap into execution plan
                single_plan = ExecutionPlan(
                    request_id=request_id,
                    session_id=session_id,
                    utterance=text,
                    actions=[PlannedAction(id=f"cmd-llm-{request_id}", tool=action, parameters=params)]
                )
                report = await execution_orchestrator.execute_plan(single_plan)
                memory_manager.record_assistant_message(session_id, report.message)
                return {
                    "type": "command_result",
                    "request_id": request_id,
                    "session_id": session_id,
                    "action": action,
                    "parameters": params,
                    "response_text": report.message,
                    "result": report.message
                }

        except Exception as e:
            logger.error(f"LLM reasoning failed: {e}")
            ans_text = "I encountered an issue connecting to the reasoning network, Minaty."

        memory_manager.record_assistant_message(session_id, ans_text)
        return {
            "type": "command_result",
            "request_id": request_id,
            "session_id": session_id,
            "action": "answer",
            "response_text": ans_text,
            "result": ans_text
        }


jarvis_brain = JarvisBrain()
