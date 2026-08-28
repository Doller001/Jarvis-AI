"""
OpenAI-Compatible & Universal Conversational Routes for Jarvis AI Assistant.
Provides endpoints for /v1/chat/completions, /models, and universal /chat /ask /query aliases.
"""

import logging
import time
import uuid
from typing import Any

from fastapi import APIRouter, Depends, Query, Request
from pydantic import BaseModel, Field

from app.agent.orchestrator import jarvis_brain
from app.llm.registry import llm_registry
from app.security.auth import optional_auth
from app.security.jwt_manager import TokenPayload

logger = logging.getLogger("jarvis.api.openai_compat")

openai_router = APIRouter(tags=["OpenAI Compatibility & Universal Chat"])


# ---------------------------------------------------------------------------
# Request & Response Schemas
# ---------------------------------------------------------------------------

class ChatMessage(BaseModel):
    role: str = "user"
    content: str = ""


class ChatCompletionRequest(BaseModel):
    model: str | None = None
    messages: list[ChatMessage] = []
    temperature: float | None = 0.7
    max_tokens: int | None = None
    stream: bool = False
    session_id: str | None = None
    request_id: str | None = None


class GenericChatRequest(BaseModel):
    text: str | None = None
    query: str | None = None
    prompt: str | None = None
    message: str | None = None
    q: str | None = None
    session_id: str = "default-session"
    request_id: str | None = None


# ---------------------------------------------------------------------------
# Helper Extraction
# ---------------------------------------------------------------------------

def extract_user_prompt(req: ChatCompletionRequest) -> str:
    """Extracts the latest user message from the messages array."""
    if not req.messages:
        return ""
    user_msgs = [m.content for m in req.messages if m.role == "user" and m.content]
    if user_msgs:
        return user_msgs[-1]
    return req.messages[-1].content


# ---------------------------------------------------------------------------
# OpenAI Compatible Endpoints
# ---------------------------------------------------------------------------

@openai_router.post("/v1/chat/completions")
@openai_router.post("/chat/completions")
async def chat_completions_post(
    req: ChatCompletionRequest,
    token: TokenPayload | None = Depends(optional_auth),
) -> dict[str, Any]:
    """Standard OpenAI-compatible chat completions POST endpoint."""
    user_text = extract_user_prompt(req)
    if not user_text:
        user_text = "Hello"

    session_id = req.session_id or (token.sub if token else f"session-{uuid.uuid4().hex[:8]}")
    request_id = req.request_id or f"req-{uuid.uuid4().hex[:8]}"

    brain_result = await jarvis_brain.process_utterance(
        text=user_text,
        session_id=session_id,
        request_id=request_id
    )

    response_text = brain_result.get("response_text") or brain_result.get("prompt") or "I am listening."

    return {
        "id": f"chatcmpl-{uuid.uuid4().hex[:12]}",
        "object": "chat.completion",
        "created": int(time.time()),
        "model": req.model or llm_registry.get_active_selection().get("model") or "jarvis-default",
        "choices": [
            {
                "index": 0,
                "message": {
                    "role": "assistant",
                    "content": response_text
                },
                "finish_reason": "stop"
            }
        ],
        "usage": {
            "prompt_tokens": max(1, len(user_text.split())),
            "completion_tokens": max(1, len(response_text.split())),
            "total_tokens": max(2, len(user_text.split()) + len(response_text.split()))
        },
        "jarvis_meta": {
            "type": brain_result.get("type"),
            "action": brain_result.get("action"),
            "parameters": brain_result.get("parameters"),
            "status": brain_result.get("status")
        }
    }


@openai_router.get("/v1/chat/completions")
@openai_router.get("/chat/completions")
async def chat_completions_get(
    q: str | None = Query(None),
    text: str | None = Query(None),
    prompt: str | None = Query(None),
    token: TokenPayload | None = Depends(optional_auth),
) -> dict[str, Any]:
    """OpenAI completions GET endpoint for probing and quick queries."""
    input_text = q or text or prompt
    if input_text:
        req = ChatCompletionRequest(messages=[ChatMessage(role="user", content=input_text)])
        return await chat_completions_post(req, token)

    return {
        "status": "healthy",
        "service": "jarvis-backend",
        "endpoint": "/v1/chat/completions",
        "protocol": "OpenAI Chat Completion v1",
        "method": "POST (recommended) / GET with ?q=...",
        "active_model": llm_registry.get_active_selection().get("model")
    }


@openai_router.get("/v1/models")
@openai_router.get("/models")
async def list_openai_models(
    token: TokenPayload | None = Depends(optional_auth),
) -> dict[str, Any]:
    """Standard OpenAI-compatible GET /v1/models endpoint."""
    providers = await llm_registry.discover_available_providers()
    model_data = []

    for p in providers:
        for m in p.models:
            model_data.append({
                "id": m,
                "object": "model",
                "created": 1700000000,
                "owned_by": p.provider.lower(),
                "permission": [],
                "root": m,
                "parent": None
            })

    if not model_data:
        model_data.append({
            "id": "jarvis-default",
            "object": "model",
            "created": 1700000000,
            "owned_by": "jarvis",
            "permission": [],
            "root": "jarvis-default",
            "parent": None
        })

    return {
        "object": "list",
        "data": model_data
    }


# ---------------------------------------------------------------------------
# Universal Convenience Chat Routes (/chat, /ask, /query, /generate, etc.)
# ---------------------------------------------------------------------------

async def _handle_generic_chat(
    input_text: str | None,
    session_id: str | None,
    request_id: str | None,
    path_name: str,
    token: TokenPayload | None = None
) -> dict[str, Any]:
    if not input_text:
        return {
            "status": "healthy",
            "service": "jarvis-backend",
            "endpoint": path_name,
            "usage": f"Send POST with JSON {{'text': 'your message'}} or GET {path_name}?q=your+message",
            "timestamp_ms": int(time.time() * 1000)
        }

    sid = session_id or (token.sub if token else f"session-{uuid.uuid4().hex[:8]}")
    rid = request_id or f"req-{uuid.uuid4().hex[:8]}"

    result = await jarvis_brain.process_utterance(
        text=input_text,
        session_id=sid,
        request_id=rid
    )
    return result


@openai_router.post("/chat")
@openai_router.post("/ask")
@openai_router.post("/query")
@openai_router.post("/generate")
@openai_router.post("/completions")
@openai_router.post("/conversation")
@openai_router.post("/message")
@openai_router.post("/send")
async def generic_chat_post(
    request: Request,
    req: GenericChatRequest,
    token: TokenPayload | None = Depends(optional_auth),
) -> dict[str, Any]:
    """Universal POST endpoint accepting JSON payload with text/query/prompt/message."""
    input_text = req.text or req.query or req.prompt or req.message or req.q
    return await _handle_generic_chat(
        input_text=input_text,
        session_id=req.session_id,
        request_id=req.request_id,
        path_name=request.url.path,
        token=token
    )


@openai_router.get("/chat")
@openai_router.get("/ask")
@openai_router.get("/query")
@openai_router.get("/generate")
@openai_router.get("/completions")
@openai_router.get("/conversation")
@openai_router.get("/message")
@openai_router.get("/send")
@openai_router.get("/api/v1/chat")
async def generic_chat_get(
    request: Request,
    q: str | None = Query(None),
    text: str | None = Query(None),
    prompt: str | None = Query(None),
    query: str | None = Query(None),
    message: str | None = Query(None),
    session_id: str | None = Query(None),
    request_id: str | None = Query(None),
    token: TokenPayload | None = Depends(optional_auth),
) -> dict[str, Any]:
    """Universal GET endpoint supporting probing or query parameters."""
    input_text = q or text or prompt or query or message
    return await _handle_generic_chat(
        input_text=input_text,
        session_id=session_id,
        request_id=request_id,
        path_name=request.url.path,
        token=token
    )
