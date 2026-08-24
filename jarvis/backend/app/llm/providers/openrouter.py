"""
Real OpenRouter API Adapter for Jarvis.
"""

import logging
import os
from collections.abc import AsyncGenerator

import httpx

from app.llm.base import (
    LLMProvider,
    LLMRequest,
    LLMResponse,
    ModelInfo,
    extract_action_and_params,
)

logger = logging.getLogger(__name__)
OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1"


class OpenRouterProvider(LLMProvider):
    def __init__(self, api_key: str | None = None):
        self._api_key = api_key
        super().__init__("openrouter", api_key=api_key)

    @property
    def api_key(self) -> str | None:
        return getattr(self, "_api_key", None) or os.getenv("OPENROUTER_API_KEY")

    @api_key.setter
    def api_key(self, value: str | None):
        self._api_key = value

    async def validate_key(self) -> bool:
        if not self.api_key:
            return False
        try:
            async with httpx.AsyncClient(timeout=5.0) as client:
                headers = {"Authorization": f"Bearer {self.api_key}"}
                resp = await client.get(f"{OPENROUTER_BASE_URL}/auth/key", headers=headers)
                return resp.status_code == 200
        except Exception:
            return False

    async def list_models(self) -> list[ModelInfo]:
        if not self.api_key:
            return []
        try:
            async with httpx.AsyncClient(timeout=5.0) as client:
                headers = {"Authorization": f"Bearer {self.api_key}"}
                resp = await client.get(f"{OPENROUTER_BASE_URL}/models", headers=headers)
                if resp.status_code == 200:
                    data = resp.json().get("data", [])
                    return [
                        ModelInfo(
                            id=m["id"],
                            name=m.get("name", m["id"]),
                            provider="openrouter",
                            context_length=m.get("context_length", 4096)
                        )
                        for m in data[:20]
                    ]
        except Exception as e:
            logger.error(f"OpenRouter models list error: {e}")
        return [
            ModelInfo(id="anthropic/claude-3.5-sonnet", name="Claude 3.5 Sonnet", provider="openrouter", context_length=200000)
        ]

    async def generate(self, request: LLMRequest) -> LLMResponse:
        if not self.api_key:
            raise ValueError("OpenRouter API key not configured")

        model = request.model or "anthropic/claude-3.5-sonnet"
        messages = []
        if request.system_prompt:
            messages.append({"role": "system", "content": request.system_prompt})
        messages.append({"role": "user", "content": request.prompt})

        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "HTTP-Referer": "https://jarvis.ai",
            "X-Title": "Jarvis AI Assistant",
            "Content-Type": "application/json"
        }
        payload = {"model": model, "messages": messages, "temperature": request.temperature}

        async with httpx.AsyncClient(timeout=15.0) as client:
            resp = await client.post(f"{OPENROUTER_BASE_URL}/chat/completions", headers=headers, json=payload)
            resp.raise_for_status()
            data = resp.json()
            content = data["choices"][0]["message"]["content"]

            action, params, confidence = extract_action_and_params(content)

            return LLMResponse(
                text=content,
                action=action or "unknown",
                parameters=params,
                confidence=confidence if action else 0.90,
                provider="openrouter",
                model=model,
                raw_response=data
            )

    async def stream(self, request: LLMRequest) -> AsyncGenerator[str, None]:
        res = await self.generate(request)
        yield res.text

    async def health_check(self) -> bool:
        return await self.validate_key()
