"""
API Routes for System Tools, Health Diagnostics, and Jarvis Status.
"""

import asyncio
from fastapi import APIRouter, Depends
from pydantic import BaseModel

from app.agent.execution_models import MultimodalInputPayload
from app.agent.orchestrator import jarvis_brain
from app.db.supabase_client import supabase_client
from app.llm.gateway import llm_gateway
from app.memory.memory_manager import memory_manager
from app.retrieval.music_index import music_index
from app.security.auth import optional_auth, require_auth
from app.security.jwt_manager import TokenPayload
from app.tools.registry import tool_registry

api_router = APIRouter(prefix="/api/v1", tags=["System API"])

ChatRequest = MultimodalInputPayload


class MusicSearchRequest(BaseModel):
    query: str
    limit: int = 5
    language: str | None = None
    mood: str | None = None
    era: str | None = None
    year_min: int | None = None
    year_max: int | None = None


@api_router.get("/health/ready")
async def health_ready():
    """Readiness probe checking memory store, database, and system readiness."""
    db_status = await asyncio.to_thread(supabase_client.ping)
    return {
        "status": "ready",
        "service": "jarvis-backend",
        "database": db_status.get("status", "unknown")
    }


@api_router.get("/health/dependencies")
async def health_dependencies():
    """Comprehensive dependency health matrix for DB, LLM providers, and vector DB."""
    db_status = await asyncio.to_thread(supabase_client.ping)
    music_status = await asyncio.to_thread(music_index.status)

    providers = llm_gateway.list_available_providers()

    return {
        "backend": "ok",
        "database": db_status.get("status", "ok"),
        "supabase": db_status,
        "music_vector_index": music_status,
        "available_llm_providers": providers
    }


@api_router.get("/music/status")
async def music_status():
    """Whether the music vector DB is loaded and how many songs it holds."""
    return await asyncio.to_thread(music_index.status)


@api_router.post("/music/search")
async def music_search(
    req: MusicSearchRequest,
    token: TokenPayload = Depends(require_auth),
):
    """Semantic song search over the local music vector DB."""
    return await asyncio.to_thread(
        music_index.search,
        req.query, req.limit, req.language, req.mood,
        req.era, req.year_min, req.year_max,
    )


@api_router.get("/tools")
async def list_tools(token: TokenPayload | None = Depends(optional_auth)):
    return {"tools": [t.model_dump() for t in tool_registry.list_tools()]}


@api_router.get("/supabase/status")
@api_router.get("/db/status")
async def supabase_db_status():
    """Returns connectivity and health status of the configured Supabase / DB instance."""
    return await asyncio.to_thread(supabase_client.ping)


@api_router.post("/chat")
async def chat(
    req: MultimodalInputPayload,
    token: TokenPayload = Depends(require_auth),
):
    sid = req.session_id if req.session_id != "default-session" else token.sub
    return await jarvis_brain.process_utterance(
        text=req.text,
        session_id=sid,
        request_id=req.request_id,
        sensory_data=req.sensory_data,
        image_base64=req.image_base64,
        image_uri=req.image_uri,
    )

