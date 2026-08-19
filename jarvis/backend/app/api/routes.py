"""
API Routes for System Tools and Jarvis Status.
"""

from fastapi import APIRouter
from app.tools.registry import tool_registry

api_router = APIRouter(prefix="/api/v1", tags=["System API"])


@api_router.get("/tools")
async def list_tools():
    return {"tools": [t.model_dump() for t in tool_registry.list_tools()]}
