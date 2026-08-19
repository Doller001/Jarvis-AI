"""
API endpoints for Jarvis LLM Provider & Model Discovery.
"""

from typing import List, Optional
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from app.llm.registry import llm_registry, ProviderStatus

providers_router = APIRouter(prefix="/api/v1", tags=["LLM Providers"])


class SelectProviderRequest(BaseModel):
    provider: str
    model: Optional[str] = None


@providers_router.get("/providers", response_model=List[ProviderStatus])
async def list_available_providers():
    return await llm_registry.discover_available_providers()


@providers_router.get("/models")
async def list_available_models():
    providers = await llm_registry.discover_available_providers()
    all_models = []
    for p in providers:
        for m in p.models:
            all_models.append(m)
    return {"models": all_models, "active_selection": llm_registry.get_active_selection()}


@providers_router.post("/providers/select")
async def select_active_provider(req: SelectProviderRequest):
    success = llm_registry.set_active_provider_and_model(req.provider, req.model)
    if not success:
        raise HTTPException(status_code=400, detail=f"Provider '{req.provider}' is not registered.")
    return {"status": "success", "active_selection": llm_registry.get_active_selection()}
