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
from app.tools.registry import tool_registry
from app.security.token_manager import token_manager
from app.memory.memory_manager import memory_manager

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
- If the user commands a device action (e.g. open app, close app, turn on/off flashlight/torch, toggle wifi/bluetooth, set volume, get time/battery/storage, call contact, send SMS/WhatsApp, read screen), output a JSON object:
  {"action": "<tool_name>", "parameters": {...}, "confidence": 0.95}
- Available tools: toggle_wifi, toggle_bluetooth, toggle_torch, set_volume, get_time, get_battery, get_storage, open_app, close_app, read_screen, call_contact, send_sms, whatsapp_send.
- If the user asks a question, chats, or seeks advice, answer directly, smartly, and concisely in natural language.
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
        llm_text_response = None
        if resolved:
            action = resolved.intent
            params = resolved.entities
            confidence = resolved.confidence
        else:
            # 2. Level-2 / Level-3 Connected LLM Reasoning
            try:
                llm_res = await llm_gateway.generate_reasoning(
                    prompt=text,
                    system_prompt=build_system_prompt(session_id, text)
                )
                action = llm_res.action or "unknown"
                params = llm_res.parameters
                confidence = llm_res.confidence
                llm_text_response = llm_res.text
            except Exception as e:
                logger.error(f"LLM reasoning failed: {e}")
                return {
                    "type": "error",
                    "request_id": request_id,
                    "code": "LLM_ERROR",
                    "message": str(e)
                }

        if action == "unknown" or not action or action == "chat" or action == "answer":
            ans_text = llm_text_response or "Hello! I am Jarvis. How can I assist you today?"
            res = {
                "type": "command_result",
                "request_id": request_id,
                "session_id": session_id,
                "action": "answer",
                "parameters": params,
                "response_text": ans_text
            }
            memory_manager.record_assistant_message(session_id, ans_text)
            return res

        # LLM output is untrusted.  Never turn an unregistered action into a
        # confirmation request or let it reach the executor as a server error.
        if not tool_registry.get_tool(action):
            logger.warning("Ignoring unregistered action from reasoning: %s", action)
            return {
                "type": "error",
                "request_id": request_id,
                "session_id": session_id,
                "code": "UNKNOWN_ACTION",
                "message": "Requested action is not supported.",
            }

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
        response_text = exec_res.get("result") or f"Jarvis executed: {action}"
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
