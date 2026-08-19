"""
JarvisBrain Canonical Orchestrator.
"""

import logging
from typing import Dict, Any

from app.agent.normalizer import intent_normalizer
from app.agent.intent_resolver import intent_resolver
from app.agent.planner import risk_policy, task_planner
from app.llm.gateway import llm_gateway
from app.tools.executor import tool_executor
from app.security.token_manager import token_manager
from app.memory.memory_manager import memory_manager

logger = logging.getLogger(__name__)

JARVIS_SYSTEM_PROMPT = """You are Jarvis, an intelligent device control and automation AI assistant.
Analyze user speech and determine the best tool action.
Return JSON: {"action": "<tool_name>", "parameters": {...}, "confidence": 0.95}
Available tools: toggle_wifi, toggle_bluetooth, toggle_torch, set_volume, get_time, open_app, read_screen, call_contact, send_sms, whatsapp_send.
"""


class JarvisBrain:
    """Jarvis Brain orchestrating local deterministic resolution & LLM fallback."""

    async def process_utterance(
        self,
        text: str,
        session_id: str = "default-session",
        request_id: str = "req-1"
    ) -> Dict[str, Any]:
        normalized = intent_normalizer.normalize(text)
        logger.info(f"JarvisBrain processing utterance: '{text}' (session: {session_id})")

        memory_manager.record_user_message(session_id, text)

        # 1. Level-1 Deterministic Fast Resolution
        resolved = intent_resolver.resolve(normalized)
        if resolved:
            action = resolved.intent
            params = resolved.entities
            confidence = resolved.confidence
        else:
            # 2. Level-2 / Level-3 Connected LLM Reasoning
            try:
                llm_res = await llm_gateway.generate_reasoning(prompt=normalized, system_prompt=JARVIS_SYSTEM_PROMPT)
                action = llm_res.action or "unknown"
                params = llm_res.parameters
                confidence = llm_res.confidence
            except Exception as e:
                logger.error(f"LLM reasoning failed: {e}")
                return {
                    "type": "error",
                    "request_id": request_id,
                    "code": "LLM_ERROR",
                    "message": str(e)
                }

        if action == "unknown":
            res = {
                "type": "command_result",
                "request_id": request_id,
                "action": "unknown",
                "parameters": params,
                "response_text": "Jarvis could not resolve an action for this utterance."
            }
            memory_manager.record_assistant_message(session_id, res["response_text"])
            return res

        # 3. Safety Risk Gate Evaluation
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

        # 4. Tool Execution & Memory Persistence
        exec_res = await tool_executor.execute_tool(action, params)
        response_text = f"Jarvis executed: {action}"
        memory_manager.record_assistant_message(session_id, response_text)

        return {
            "type": "command_result",
            "request_id": request_id,
            "session_id": session_id,
            "action": action,
            "parameters": params,
            "response_text": response_text,
            "execution_result": exec_res
        }


jarvis_brain = JarvisBrain()
