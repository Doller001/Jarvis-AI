"""
Real Groq API Adapter for Jarvis.
"""

import os
import logging
from typing import List, AsyncGenerator, Optional
import httpx

from app.llm.base import LLMProvider, ModelInfo, LLMRequest, LLMResponse, extract_action_and_params

logger = logging.getLogger(__name__)
GROQ_BASE_URL = "https://api.groq.com/openai/v1"


class GroqProvider(LLMProvider):
    def __init__(self, api_key: Optional[str] = None):
        self._api_key = api_key
        super().__init__("groq", api_key=api_key)

    @property
    def api_key(self) -> Optional[str]:
        return getattr(self, "_api_key", None) or os.getenv("GROQ_API_KEY")

    @api_key.setter
    def api_key(self, value: Optional[str]):
        self._api_key = value

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
                    # Exclude non-chat whisper/audio models
                    chat_models = [m for m in data if not m["id"].startswith("whisper") and not m["id"].startswith("canopylabs")]
                    if chat_models:
                        return [
                            ModelInfo(
                                id=m["id"],
                                name=f"Groq {m['id']}",
                                provider="groq",
                                context_length=m.get("context_window", 131072)
                            )
                            for m in chat_models
                        ]
        except Exception as e:
            logger.error(f"Groq models list error: {e}")
        return [
            ModelInfo(id="groq/compound", name="Groq Compound", provider="groq", context_length=131072),
            ModelInfo(id="groq/compound-mini", name="Groq Compound Mini", provider="groq", context_length=131072),
            ModelInfo(id="qwen/qwen3.6-27b", name="Qwen 3.6 27B", provider="groq", context_length=131072)
        ]

    async def generate(self, request: LLMRequest) -> LLMResponse:
        if not self.api_key:
            raise ValueError("Groq API key not configured")

        model = request.model or "groq/compound"
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

            action, params, confidence = extract_action_and_params(content)

            return LLMResponse(
                text=content,
                action=action or "unknown",
                parameters=params,
                confidence=confidence if action else 0.95,
                provider="groq",
                model=model,
                raw_response=data
            )

    async def stream(self, request: LLMRequest) -> AsyncGenerator[str, None]:
        res = await self.generate(request)
        yield res.text

    async def health_check(self) -> bool:
        return await self.validate_key()
