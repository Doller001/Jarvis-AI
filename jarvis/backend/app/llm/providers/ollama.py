"""
Real Ollama Local API Adapter for Jarvis.
With proper health checking and graceful degradation.
"""

import logging
import os
import time
from collections.abc import AsyncGenerator
from dataclasses import dataclass

import httpx

from app.llm.base import (
    LLMProvider,
    LLMRequest,
    LLMResponse,
    ModelInfo,
    extract_action_and_params,
)

logger = logging.getLogger(__name__)
OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")


@dataclass
class HealthStatus:
    available: bool
    reason: str  # "ok", "service_unreachable", "no_models", "timeout", "error"
    last_checked: float
    models_count: int = 0


class OllamaProvider(LLMProvider):
    def __init__(self, base_url: str | None = None):
        url = base_url or OLLAMA_BASE_URL
        super().__init__("ollama", api_key=None)
        self.base_url = url.rstrip("/")
        self._health_status: HealthStatus | None = None
        self._last_health_check = 0.0
        self._health_check_interval = 30.0  # seconds between health checks

    async def validate_key(self) -> bool:
        health = await self._check_health()
        return health.available

    async def health_check(self) -> bool:
        health = await self._check_health()
        return health.available

    async def _check_health(self) -> HealthStatus:
        now = time.time()

        # Return cached health if recent
        if self._health_status and (now - self._last_health_check) < self._health_check_interval:
            return self._health_status

        try:
            async with httpx.AsyncClient(timeout=3.0) as client:
                resp = await client.get(f"{self.base_url}/api/tags")
                if resp.status_code == 200:
                    models_data = resp.json().get("models", [])
                    self._health_status = HealthStatus(
                        available=True,
                        reason="ok",
                        last_checked=now,
                        models_count=len(models_data),
                    )
                else:
                    self._health_status = HealthStatus(
                        available=False,
                        reason=f"http_{resp.status_code}",
                        last_checked=now,
                    )
        except httpx.ConnectError:
            self._health_status = HealthStatus(
                available=False,
                reason="service_unreachable",
                last_checked=now,
            )
        except httpx.TimeoutException:
            self._health_status = HealthStatus(
                available=False,
                reason="timeout",
                last_checked=now,
            )
        except Exception as e:
            self._health_status = HealthStatus(
                available=False,
                reason=f"error: {str(e)[:100]}",
                last_checked=now,
            )

        self._last_health_check = now

        if not self._health_status.available:
            logger.warning(
                f"Ollama health check failed: {self._health_status.reason} "
                f"(base_url={self.base_url})"
            )

        return self._health_status

    def get_health_status(self) -> dict:
        if self._health_status is None:
            return {
                "provider": "ollama",
                "available": False,
                "reason": "not_checked",
                "last_checked": None,
            }
        return {
            "provider": "ollama",
            "available": self._health_status.available,
            "reason": self._health_status.reason,
            "last_checked": self._health_status.last_checked,
            "models_count": self._health_status.models_count,
            "base_url": self.base_url,
        }

    async def list_models(self) -> list[ModelInfo]:
        health = await self._check_health()
        if not health.available:
            logger.info(f"Ollama not available ({health.reason}). Returning empty model list.")
            return []

        try:
            async with httpx.AsyncClient(timeout=3.0) as client:
                resp = await client.get(f"{self.base_url}/api/tags")
                if resp.status_code == 200:
                    models_data = resp.json().get("models", [])
                    return [
                        ModelInfo(
                            id=m["name"],
                            name=f"Ollama {m['name']}",
                            provider="ollama",
                            context_length=8192
                        )
                        for m in models_data
                    ]
        except Exception as e:
            logger.warning(f"Failed to list Ollama models: {e}")
        return []

    async def generate(self, request: LLMRequest) -> LLMResponse:
        health = await self._check_health()
        if not health.available:
            raise ConnectionError(f"Ollama is not available: {health.reason}")

        model = request.model or "llama3"
        payload = {
            "model": model,
            "prompt": request.prompt,
            "system": request.system_prompt or "",
            "stream": False
        }

        async with httpx.AsyncClient(timeout=4.0) as client:
            resp = await client.post(f"{self.base_url}/api/generate", json=payload)
            resp.raise_for_status()
            data = resp.json()
            response_text = data.get("response", "")

            action, params, confidence = extract_action_and_params(response_text)

            return LLMResponse(
                text=response_text,
                action=action or "unknown",
                parameters=params,
                confidence=confidence if action else 0.85,
                provider="ollama",
                model=model,
                raw_response=data
            )

    async def stream(self, request: LLMRequest) -> AsyncGenerator[str, None]:
        res = await self.generate(request)
        yield res.text
