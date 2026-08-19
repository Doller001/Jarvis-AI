"""
Real Groq API Adapter for Jarvis.
"""

import os
import json
import logging
from typing import List, AsyncGenerator, Optional
import httpx

from app.llm.base import LLMProvider, ModelInfo, LLMRequest, LLMResponse

logger = logging.getLogger(__name__)
GROQ_BASE_URL = "https://api.groq.com/openai/v1"


class GroqProvider(LLMProvider):
    def __init__(self, api_key: Optional[str] = None):
        key = api_key or os.getenv("GROQ_API_KEY")
        super().__init__("groq", api_key=key)

    async def validate_key(self) -> bool:
        if not self.api_key:
            return False
        try:
            async with httpx.AsyncClient(timeout=5.0) as client:
                headers = {"Authorization": f"Bearer {self.api_key}"}
                resp = await client.get(f"{GROQ_BASE_URL}/models", headers=headers)
                return resp.status_code == 200
        except Exception:
            return False

    async def list_models(self) -> List[ModelInfo]:
        if not self.api_key:
            return []
        try:
            async with httpx.AsyncClient(timeout=5.0) as client:
                headers = {"Authorization": f"Bearer {self.api_key}"}
                resp = await client.get(f"{GROQ_BASE_URL}/models", headers=headers)
                if resp.status_code == 200:
                    data = resp.json().get("data", [])
                    return [
                        ModelInfo(
                            id=m["id"],
                            name=f"Groq {m['id']}",
                            provider="groq",
                            context_length=m.get("context_window", 131072)
                        )
                        for m in data
                    ]
        except Exception as e:
            logger.error(f"Groq models list error: {e}")
        return [
            ModelInfo(id="llama-3.3-70b-versatile", name="Groq Llama 3.3 70B", provider="groq", context_length=131072)
        ]

    async def generate(self, request: LLMRequest) -> LLMResponse:
        if not self.api_key:
            raise ValueError("Groq API key not configured")

        model = request.model or "llama-3.3-70b-versatile"
        messages = []
        if request.system_prompt:
            messages.append({"role": "system", "content": request.system_prompt})
        messages.append({"role": "user", "content": request.prompt})

        headers = {"Authorization": f"Bearer {self.api_key}", "Content-Type": "application/json"}
        payload = {"model": model, "messages": messages, "temperature": request.temperature}

        async with httpx.AsyncClient(timeout=15.0) as client:
            resp = await client.post(f"{GROQ_BASE_URL}/chat/completions", headers=headers, json=payload)
            resp.raise_for_status()
            data = resp.json()
            content = data["choices"][0]["message"]["content"]

            action = "unknown"
            params = {}
            confidence = 0.95
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
                provider="groq",
                model=model,
                raw_response=data
            )

    async def stream(self, request: LLMRequest) -> AsyncGenerator[str, None]:
        res = await self.generate(request)
        yield res.text

    async def health_check(self) -> bool:
        return await self.validate_key()
