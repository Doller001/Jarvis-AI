"""
Real Ollama Local API Adapter for Jarvis.
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
OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")


class OllamaProvider(LLMProvider):
    def __init__(self, base_url: str | None = None):
        url = base_url or OLLAMA_BASE_URL
        super().__init__("ollama", api_key=None)
        self.base_url = url.rstrip("/")

    async def validate_key(self) -> bool:
        return await self.health_check()

    async def health_check(self) -> bool:
        try:
            async with httpx.AsyncClient(timeout=3.0) as client:
                resp = await client.get(f"{self.base_url}/api/tags")
                return resp.status_code == 200
        except Exception:
            return False

    async def list_models(self) -> list[ModelInfo]:
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
        except Exception:
            pass
        return []

    async def generate(self, request: LLMRequest) -> LLMResponse:
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
