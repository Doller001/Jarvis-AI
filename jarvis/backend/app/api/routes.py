"""
API Routes for System Tools and Jarvis Status.
"""


from fastapi import APIRouter
from pydantic import BaseModel

from app.agent.orchestrator import jarvis_brain
from app.retrieval.music_index import music_index
from app.tools.registry import tool_registry

api_router = APIRouter(prefix="/api/v1", tags=["System API"])


class ChatRequest(BaseModel):
    text: str
    session_id: str = "default-session"
    request_id: str = "req-http"


class MusicSearchRequest(BaseModel):
    query: str
    limit: int = 5
    language: str | None = None
    mood: str | None = None
    era: str | None = None
    year_min: int | None = None
    year_max: int | None = None


@api_router.get("/music/status")
async def music_status():
    """Whether the music vector DB is loaded and how many songs it holds."""
    import asyncio
    return await asyncio.to_thread(music_index.status)


@api_router.post("/music/search")
async def music_search(req: MusicSearchRequest):
    """Semantic song search over the local music vector DB."""
    import asyncio
    return await asyncio.to_thread(
        music_index.search,
        req.query, req.limit, req.language, req.mood,
        req.era, req.year_min, req.year_max,
    )


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
