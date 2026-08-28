"""
Authentication, CORS, and Security Utilities for Jarvis.
"""

import logging
import os

from fastapi import Header, HTTPException, Request

from app.security.jwt_manager import TokenPayload, jwt_manager
from app.security.device_registry import device_registry

logger = logging.getLogger("jarvis.security")


# ---------------------------------------------------------------------------
# CORS
# ---------------------------------------------------------------------------

def get_allowed_origins() -> list[str]:
    env = os.getenv("ENVIRONMENT", "development").lower()
    env_origins = os.getenv("ALLOWED_ORIGINS", "")

    if env_origins:
        origins = [o.strip() for o in env_origins.split(",") if o.strip()]
        if origins:
            if env == "production" and "*" in origins:
                logger.critical(
                    "CORS wildcard '*' is not allowed in production. "
                    "Falling back to empty allowlist."
                )
                return []
            return origins

    if env == "production":
        return []
    return ["http://localhost:3000", "http://127.0.0.1:3000", "http://localhost:8000"]


# ---------------------------------------------------------------------------
# JWT Validation
# ---------------------------------------------------------------------------

def validate_token(token: str) -> TokenPayload | None:
    return jwt_manager.validate_token(token)


async def require_auth(
    request: Request,
    authorization: str | None = Header(None),
) -> TokenPayload:
    """FastAPI dependency that requires a valid JWT Bearer token."""
    if not authorization:
        raise HTTPException(status_code=401, detail="Missing Authorization header")

    parts = authorization.split()
    if len(parts) != 2 or parts[0].lower() != "bearer":
        raise HTTPException(status_code=401, detail="Invalid Authorization format")

    token = parts[1]
    payload = jwt_manager.validate_token(token)

    if payload is None:
        raise HTTPException(status_code=401, detail="Invalid or expired token")

    if payload.token_type != "access":
        raise HTTPException(status_code=401, detail="Token type not authorized")

    device = device_registry.get_device(payload.sub)
    if device is None:
        raise HTTPException(status_code=401, detail="Unknown device")

    device_registry.touch_device(payload.sub)
    return payload


async def optional_auth(
    request: Request,
    authorization: str | None = Header(None),
) -> TokenPayload | None:
    """FastAPI dependency that parses JWT Bearer token if present, returning None if unauthenticated."""
    if not authorization:
        return None

    parts = authorization.split()
    if len(parts) != 2 or parts[0].lower() != "bearer":
        return None

    token = parts[1]
    payload = jwt_manager.validate_token(token)

    if payload is None or payload.token_type != "access":
        return None

    device = device_registry.get_device(payload.sub)
    if device is not None:
        device_registry.touch_device(payload.sub)

    return payload

