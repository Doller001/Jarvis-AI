"""
JWT Token Manager for Jarvis AI Backend.
Handles access/refresh token creation, validation, and refresh.
"""

import logging
import os
import secrets
import time
from dataclasses import dataclass
from typing import Any

import jwt

logger = logging.getLogger("jarvis.security.jwt")

DEFAULT_DEV_SECRET = "jarvis-dev-secret-change-in-production"
JWT_SECRET = os.getenv("JARVIS_JWT_SECRET", DEFAULT_DEV_SECRET)
JWT_ALGORITHM = "HS256"
ACCESS_TOKEN_TTL_MINUTES = int(os.getenv("JWT_ACCESS_TTL_MINUTES", "15"))
REFRESH_TOKEN_TTL_DAYS = int(os.getenv("JWT_REFRESH_TTL_DAYS", "30"))


@dataclass
class TokenPayload:
    sub: str          # device_id
    iat: float        # issued at
    exp: float        # expiration
    token_type: str   # "access" or "refresh"
    jti: str | None = None
    session_id: str | None = None
    roles: list[str] | None = None


@dataclass
class TokenPair:
    access_token: str
    refresh_token: str
    expires_in: int  # seconds until access token expires
    session_id: str


class JWTManager:
    """Manages JWT access and refresh tokens for device authentication with rotation."""

    def __init__(self, secret: str | None = None, algorithm: str | None = None):
        self._secret = secret or JWT_SECRET
        self._algorithm = algorithm or JWT_ALGORITHM
        self._validate_production_secret()

    def _validate_production_secret(self) -> None:
        env = os.getenv("ENVIRONMENT", "development").lower()
        if env == "production":
            if not self._secret or self._secret == DEFAULT_DEV_SECRET or len(self._secret) < 16:
                logger.critical("FATAL: JARVIS_JWT_SECRET is missing, insecure, or using dev default in production!")
                raise RuntimeError(
                    "Production boot failed: JARVIS_JWT_SECRET must be set to a secure secret of at least 16 characters in production."
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
        sid = session_id or f"sess-{secrets.token_hex(8)}"
        payload: dict[str, Any] = {
            "sub": device_id,
            "iat": now,
            "exp": now + (ttl * 60),
            "token_type": "access",
            "jti": secrets.token_hex(16),
            "session_id": sid,
        }
        if roles:
            payload["roles"] = roles
        return jwt.encode(payload, self._secret, algorithm=self._algorithm)

    def create_refresh_token(
        self,
        device_id: str,
        session_id: str | None = None,
        ttl_days: int | None = None,
    ) -> str:
        ttl = ttl_days or REFRESH_TOKEN_TTL_DAYS
        now = time.time()
        sid = session_id or f"sess-{secrets.token_hex(8)}"
        payload: dict[str, Any] = {
            "sub": device_id,
            "iat": now,
            "exp": now + (ttl * 86400),
            "token_type": "refresh",
            "jti": secrets.token_hex(16),
            "session_id": sid,
        }
        return jwt.encode(payload, self._secret, algorithm=self._algorithm)

    def create_token_pair(
        self,
        device_id: str,
        session_id: str | None = None,
        roles: list[str] | None = None,
    ) -> TokenPair:
        sid = session_id or f"sess-{secrets.token_hex(8)}"
        access = self.create_access_token(device_id, sid, roles)
        refresh = self.create_refresh_token(device_id, sid)
        return TokenPair(
            access_token=access,
            refresh_token=refresh,
            expires_in=ACCESS_TOKEN_TTL_MINUTES * 60,
            session_id=sid,
        )

    def validate_token(self, token: str) -> TokenPayload | None:
        try:
            decoded = jwt.decode(token, self._secret, algorithms=[self._algorithm])
            return TokenPayload(
                sub=decoded["sub"],
                iat=decoded["iat"],
                exp=decoded["exp"],
                token_type=decoded.get("token_type", "access"),
                jti=decoded.get("jti"),
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
        return self.create_access_token(device_id=payload.sub, session_id=payload.session_id)


jwt_manager = JWTManager()

