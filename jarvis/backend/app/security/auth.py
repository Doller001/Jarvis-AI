"""
Authentication, CORS, and Security Utilities for Jarvis.
"""

import logging
import os

logger = logging.getLogger("jarvis.security")


def get_allowed_origins() -> list[str]:
    env_origins = os.getenv("ALLOWED_ORIGINS", "")
    if env_origins:
        origins = [o.strip() for o in env_origins.split(",") if o.strip()]
        if origins:
            return origins
    return ["http://localhost:3000", "http://127.0.0.1:3000", "http://localhost:8000"]


def validate_ws_token(token: str | None) -> bool:
    env = os.getenv("ENVIRONMENT", "development").lower()
    auth_required = os.getenv("JARVIS_WS_AUTH_REQUIRED", "false").lower() == "true"
    expected_token = os.getenv("JARVIS_WS_AUTH_TOKEN")

    if env == "production" or auth_required:
        if not expected_token:
            logger.error("Production security policy violation: JARVIS_WS_AUTH_TOKEN is required in production.")
            return False
        return token == expected_token

    if not expected_token:
        return True
    return token == expected_token
