"""
Real OpenRouter API Adapter for Jarvis.
"""

import os
import json
import logging
from typing import List, AsyncGenerator, Optional
import httpx

from app.llm.base import LLMProvider, ModelInfo, LLMRequest, LLMResponse

logger = logging.getLogger(__name__)
OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1"


class OpenRouterProvider(LLMProvider):
    def __init__(self, api_key: Optional[str] = None):
        super().__init__("openrouter", api_key=api_key)

    @property
    def api_key(self) -> Optional[str]:
        return self._api_key or os.getenv("OPENROUTER_API_KEY")

    @api_key.setter
    def api_key(self, value: Optional[str]):
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

    async def list_models(self) -> List[ModelInfo]:
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

            action = "unknown"
            params = {}
            confidence = 0.90
            try:
                if content.strip().startswith("{"):
                    parsed = json.loads(content)
                    action = parsed.get("action", action)
                    params = parsed.get("parameters", params)
                    confidence = parsed.get("confidence", confidence)
            except Exception:
                pass

            return LLMResponse(
                text=content,
                action=action,
                parameters=params,
                confidence=confidence,
                provider="openrouter",
                model=model,
                raw_response=data
            )

    async def stream(self, request: LLMRequest) -> AsyncGenerator[str, None]:
        res = await self.generate(request)
        yield res.text

    async def health_check(self) -> bool:
        return await self.validate_key()
