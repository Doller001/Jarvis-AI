"""
JWT Token Manager for Jarvis AI Backend.
Handles access/refresh token creation, validation, and refresh.
"""

import logging
import os
import time
from dataclasses import dataclass
from typing import Any

import jwt

logger = logging.getLogger("jarvis.security.jwt")

JWT_SECRET = os.getenv("JARVIS_JWT_SECRET", "jarvis-dev-secret-change-in-production")
JWT_ALGORITHM = "HS256"
ACCESS_TOKEN_TTL_MINUTES = int(os.getenv("JWT_ACCESS_TTL_MINUTES", "15"))
REFRESH_TOKEN_TTL_DAYS = int(os.getenv("JWT_REFRESH_TTL_DAYS", "30"))


@dataclass
class TokenPayload:
    sub: str          # device_id
    iat: float        # issued at
    exp: float        # expiration
    token_type: str   # "access" or "refresh"
    session_id: str | None = None
    roles: list[str] | None = None


@dataclass
class TokenPair:
    access_token: str
    refresh_token: str
    expires_in: int  # seconds until access token expires


class JWTManager:
    """Manages JWT access and refresh tokens for device authentication."""

    def __init__(self, secret: str | None = None, algorithm: str | None = None):
        self._secret = secret or JWT_SECRET
        self._algorithm = algorithm or JWT_ALGORITHM

        if self._secret == "jarvis-dev-secret-change-in-production":
            env = os.getenv("ENVIRONMENT", "development").lower()
            if env == "production":
                logger.critical(
                    "JWT_SECRET is set to the default development value in production! "
                    "Set JARVIS_JWT_SECRET to a strong random string."
                )

    def create_access_token(
        self,
        device_id: str,
        session_id: str | None = None,
        roles: list[str] | None = None,
        ttl_minutes: int | None = None,
    ) -> str:
        ttl = ttl_minutes or ACCESS_TOKEN_TTL_MINUTES
        now = time.time()
        payload: dict[str, Any] = {
            "sub": device_id,
            "iat": now,
            "exp": now + (ttl * 60),
            "token_type": "access",
        }
        if session_id:
            payload["session_id"] = session_id
        if roles:
            payload["roles"] = roles
        return jwt.encode(payload, self._secret, algorithm=self._algorithm)

    def create_refresh_token(
        self,
        device_id: str,
        ttl_days: int | None = None,
    ) -> str:
        ttl = ttl_days or REFRESH_TOKEN_TTL_DAYS
        now = time.time()
        payload: dict[str, Any] = {
            "sub": device_id,
            "iat": now,
            "exp": now + (ttl * 86400),
            "token_type": "refresh",
        }
        return jwt.encode(payload, self._secret, algorithm=self._algorithm)

    def create_token_pair(
        self,
        device_id: str,
        session_id: str | None = None,
        roles: list[str] | None = None,
    ) -> TokenPair:
        access = self.create_access_token(device_id, session_id, roles)
        refresh = self.create_refresh_token(device_id)
        return TokenPair(
            access_token=access,
            refresh_token=refresh,
            expires_in=ACCESS_TOKEN_TTL_MINUTES * 60,
        )

    def validate_token(self, token: str) -> TokenPayload | None:
        try:
            decoded = jwt.decode(token, self._secret, algorithms=[self._algorithm])
            return TokenPayload(
                sub=decoded["sub"],
                iat=decoded["iat"],
                exp=decoded["exp"],
                token_type=decoded.get("token_type", "access"),
                session_id=decoded.get("session_id"),
                roles=decoded.get("roles"),
            )
        except jwt.ExpiredSignatureError:
            logger.debug("Token expired")
            return None
        except jwt.InvalidTokenError as e:
            logger.debug(f"Invalid token: {e}")
            return None

    def refresh_access_token(self, refresh_token: str) -> str | None:
        payload = self.validate_token(refresh_token)
        if payload is None or payload.token_type != "refresh":
            logger.warning("Invalid or non-refresh token used for refresh")
            return None
        return self.create_access_token(device_id=payload.sub)


jwt_manager = JWTManager()
