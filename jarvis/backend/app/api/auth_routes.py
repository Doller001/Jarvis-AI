"""
Auth API routes for device registration and token management.
"""

import logging
from typing import Any

from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel

from app.security.device_registry import device_registry
from app.security.jwt_manager import jwt_manager

logger = logging.getLogger("jarvis.api.auth")

auth_router = APIRouter(prefix="/api/v1/auth", tags=["Auth"])


class DeviceRegistrationRequest(BaseModel):
    device_name: str
    device_model: str
    os_version: str
    device_id: str | None = None
    trust_token: str | None = None


class TokenRefreshRequest(BaseModel):
    refresh_token: str


class DeviceRegistrationResponse(BaseModel):
    device_id: str
    trust_token: str | None = None
    first_time: bool


class TokenResponse(BaseModel):
    access_token: str
    refresh_token: str
    expires_in: int
    token_type: str = "bearer"
    device_id: str
    trusted: bool


@auth_router.post("/register", response_model=DeviceRegistrationResponse)
async def register_device(req: DeviceRegistrationRequest) -> dict[str, Any]:
    existing = device_registry.get_device(req.device_id) if req.device_id else None
    identity = device_registry.register_device(
        device_name=req.device_name,
        device_model=req.device_model,
        os_version=req.os_version,
        device_id=req.device_id,
    )

    return {
        "device_id": identity.device_id,
        "trust_token": identity.trust_token if not identity.trusted else None,
        "first_time": existing is None,
    }


@auth_router.post("/trust")
async def trust_device(request: Request, trust_token: str | None = None) -> dict[str, str]:
    if not trust_token:
        body = await request.json()
        trust_token = body.get("trust_token")

    if not trust_token:
        raise HTTPException(status_code=400, detail="trust_token is required")

    device_id = device_registry._trust_tokens.get(trust_token)
    if not device_id:
        raise HTTPException(status_code=404, detail="Invalid trust token")

    success = device_registry.trust_device(device_id)
    if not success:
        raise HTTPException(status_code=404, detail="Device not found")

    return {"status": "trusted", "device_id": device_id}


@auth_router.post("/token", response_model=TokenResponse)
async def exchange_token(req: DeviceRegistrationRequest) -> dict[str, Any]:
    identity = device_registry.register_device(
        device_name=req.device_name,
        device_model=req.device_model,
        os_version=req.os_version,
        device_id=req.device_id,
    )

    token_pair = jwt_manager.create_token_pair(
        device_id=identity.device_id,
    )

    return {
        "access_token": token_pair.access_token,
        "refresh_token": token_pair.refresh_token,
        "expires_in": token_pair.expires_in,
        "token_type": "bearer",
        "device_id": identity.device_id,
        "trusted": identity.trusted,
    }


@auth_router.post("/refresh", response_model=TokenResponse)
async def refresh_token(req: TokenRefreshRequest) -> dict[str, Any]:
    payload = jwt_manager.validate_token(req.refresh_token)
    if payload is None or payload.token_type != "refresh":
        raise HTTPException(status_code=401, detail="Invalid refresh token")

    new_access = jwt_manager.refresh_access_token(req.refresh_token)
    if new_access is None:
        raise HTTPException(status_code=401, detail="Refresh failed")

    device = device_registry.get_device(payload.sub)
    new_refresh = jwt_manager.create_refresh_token(device_id=payload.sub)

    return {
        "access_token": new_access,
        "refresh_token": new_refresh,
        "expires_in": 900,
        "token_type": "bearer",
        "device_id": payload.sub,
        "trusted": device.trusted if device else False,
    }


@auth_router.get("/devices")
async def list_devices() -> list[dict[str, Any]]:
    devices = device_registry.list_devices()
    return [
        {
            "device_id": d.device_id,
            "device_name": d.device_name,
            "device_model": d.device_model,
            "first_seen": d.first_seen,
            "last_seen": d.last_seen,
            "trusted": d.trusted,
        }
        for d in devices
    ]
