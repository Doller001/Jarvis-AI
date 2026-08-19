"""
Cryptographically secure single-use confirmation token manager.
"""

import time
import secrets
from typing import Dict, Optional, Any
from dataclasses import dataclass

@dataclass
class ConfirmationTokenPayload:
    token: str
    session_id: str
    request_id: str
    action: str
    parameters: Dict[str, Any]
    created_at: float
    expires_at: float
    used: bool = False


class ConfirmationTokenManager:
    def __init__(self, ttl_seconds: float = 300.0) -> None:
        self.ttl_seconds = ttl_seconds
        self._tokens: Dict[str, ConfirmationTokenPayload] = {}

    def create_token(
        self,
        session_id: str,
        request_id: str,
        action: str,
        parameters: Dict[str, Any]
    ) -> ConfirmationTokenPayload:
        token_str = secrets.token_urlsafe(32)
        now = time.time()
        payload = ConfirmationTokenPayload(
            token=token_str,
            session_id=session_id,
            request_id=request_id,
            action=action,
            parameters=parameters,
            created_at=now,
            expires_at=now + self.ttl_seconds,
            used=False
        )
        self._tokens[token_str] = payload
        return payload

    def validate_and_consume(
        self,
        token_str: str,
        session_id: str,
        expected_action: Optional[str] = None
    ) -> Optional[ConfirmationTokenPayload]:
        self._purge_expired()

        payload = self._tokens.get(token_str)
        if not payload or payload.used or payload.session_id != session_id:
            return None

        if expected_action and payload.action != expected_action:
            return None

        if time.time() > payload.expires_at:
            del self._tokens[token_str]
            return None

        payload.used = True
        del self._tokens[token_str]
        return payload

    def _purge_expired(self) -> None:
        now = time.time()
        expired = [t for t, p in self._tokens.items() if now > p.expires_at or p.used]
        for t in expired:
            self._tokens.pop(t, None)


token_manager = ConfirmationTokenManager()
