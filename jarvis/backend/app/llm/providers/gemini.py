"""
Real Google Gemini API Adapter for Jarvis.
"""

import os
import logging
from typing import List, AsyncGenerator, Optional
import httpx

from app.llm.base import LLMProvider, ModelInfo, LLMRequest, LLMResponse, extract_action_and_params

logger = logging.getLogger(__name__)
GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"


class GeminiProvider(LLMProvider):
    def __init__(self, api_key: Optional[str] = None):
        self._api_key = api_key
        super().__init__("gemini", api_key=api_key)

    @property
    def api_key(self) -> Optional[str]:
        return getattr(self, "_api_key", None) or os.getenv("GEMINI_API_KEY")

    @api_key.setter
    def api_key(self, value: Optional[str]):
        self._api_key = value

    async def validate_key(self) -> bool:
        if not self.api_key:
            return False
        try:
            async with httpx.AsyncClient(timeout=5.0) as client:
                resp = await client.get(f"{GEMINI_BASE_URL}/models?key={self.api_key}")
                return resp.status_code == 200
        except Exception:
            return False

    async def list_models(self) -> List[ModelInfo]:
        if not self.api_key:
            return []
        try:
            async with httpx.AsyncClient(timeout=5.0) as client:
                resp = await client.get(f"{GEMINI_BASE_URL}/models?key={self.api_key}")
                if resp.status_code == 200:
                    data = resp.json().get("models", [])
                    models = []
                    for m in data:
                        model_name = m.get("name", "").replace("models/", "")
                        if "gemini" in model_name:
                            models.append(
                                ModelInfo(
                                    id=model_name,
                                    name=m.get("displayName", model_name),
                                    provider="gemini",
                                    context_length=m.get("inputTokenLimit", 1048576)
                                )
                            )
                    return models
        except Exception as e:
            logger.error(f"Gemini models list error: {e}")
        return [
            ModelInfo(id="gemini-1.5-flash", name="Gemini 1.5 Flash", provider="gemini", context_length=1048576)
        ]

    async def generate(self, request: LLMRequest) -> LLMResponse:
        if not self.api_key:
            raise ValueError("Gemini API key not configured")

        model = request.model or "gemini-1.5-flash"
        contents = []
        if request.system_prompt:
            contents.append({"role": "user", "parts": [{"text": f"System: {request.system_prompt}"}]})
        contents.append({"role": "user", "parts": [{"text": request.prompt}]})

        url = f"{GEMINI_BASE_URL}/models/{model}:generateContent?key={self.api_key}"

        async with httpx.AsyncClient(timeout=15.0) as client:
            resp = await client.post(url, json={"contents": contents})
            resp.raise_for_status()
            data = resp.json()
            candidates = data.get("candidates", [])
            text = ""
            if candidates and "content" in candidates[0]:
                parts = candidates[0]["content"].get("parts", [])
                if parts:
                    text = parts[0].get("text", "")

            action, params, confidence = extract_action_and_params(text)

            return LLMResponse(
                text=text,
                action=action or "unknown",
                parameters=params,
                confidence=confidence if action else 0.92,
                provider="gemini",
                model=model,
                raw_response=data
            )

    async def stream(self, request: LLMRequest) -> AsyncGenerator[str, None]:
        res = await self.generate(request)
        yield res.text

    async def health_check(self) -> bool:
        return await self.validate_key()
