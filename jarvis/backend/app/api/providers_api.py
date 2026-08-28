"""
API endpoints for Jarvis LLM Provider & Model Discovery.
"""


from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel

from app.llm.registry import ProviderStatus, llm_registry
from app.security.auth import require_auth
from app.security.jwt_manager import TokenPayload

providers_router = APIRouter(prefix="/api/v1", tags=["LLM Providers"])


class SelectProviderRequest(BaseModel):
    provider: str
    model: str | None = None


@providers_router.get("/providers", response_model=list[ProviderStatus])
async def list_available_providers(token: TokenPayload = Depends(require_auth)):
    return await llm_registry.discover_available_providers()


@providers_router.get("/models")
async def list_available_models(token: TokenPayload = Depends(require_auth)):
    providers = await llm_registry.discover_available_providers()
    all_models = []
    for p in providers:
        for m in p.models:
            all_models.append(m)
    return {"models": all_models, "active_selection": llm_registry.get_active_selection()}


@providers_router.post("/providers/select")
async def select_active_provider(
    req: SelectProviderRequest,
    token: TokenPayload = Depends(require_auth),
):
    success = llm_registry.set_active_provider_and_model(req.provider, req.model)
    if not success:
        raise HTTPException(status_code=400, detail=f"Provider '{req.provider}' is not registered.")
    return {"status": "success", "active_selection": llm_registry.get_active_selection()}
