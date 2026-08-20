"""
API Routes for System Tools and Jarvis Status.
"""

from fastapi import APIRouter
from pydantic import BaseModel

from app.tools.registry import tool_registry
from app.agent.orchestrator import jarvis_brain

api_router = APIRouter(prefix="/api/v1", tags=["System API"])


class ChatRequest(BaseModel):
    text: str
    session_id: str = "default-session"
    request_id: str = "req-http"


@api_router.get("/tools")
async def list_tools():
    return {"tools": [t.model_dump() for t in tool_registry.list_tools()]}


@api_router.post("/chat")
async def chat(req: ChatRequest):
    return await jarvis_brain.process_utterance(
        text=req.text,
        session_id=req.session_id,
        request_id=req.request_id
    )
