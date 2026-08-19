"""
Real NVIDIA NIM API Adapter for Jarvis AI Assistant.
"""

import os
import json
import logging
from typing import List, AsyncGenerator, Optional
import httpx

from app.llm.base import LLMProvider, ModelInfo, LLMRequest, LLMResponse

logger = logging.getLogger(__name__)
NVIDIA_BASE_URL = "https://integrate.api.nvidia.com/v1"


class NVIDIAProvider(LLMProvider):
    def __init__(self, api_key: Optional[str] = None):
        super().__init__("nvidia", api_key=api_key)

    @property
    def api_key(self) -> Optional[str]:
        return self._api_key or os.getenv("NVIDIA_API_KEY")

    @api_key.setter
    def api_key(self, value: Optional[str]):
        self._api_key = value

    async def validate_key(self) -> bool:
        if not self.api_key:
            return False
        try:
            async with httpx.AsyncClient(timeout=5.0) as client:
                headers = {"Authorization": f"Bearer {self.api_key}"}
                resp = await client.get(f"{NVIDIA_BASE_URL}/models", headers=headers)
                return resp.status_code == 200
        except Exception:
            return False

    async def list_models(self) -> List[ModelInfo]:
        if not self.api_key:
            return []
        try:
            async with httpx.AsyncClient(timeout=5.0) as client:
                headers = {"Authorization": f"Bearer {self.api_key}"}
                resp = await client.get(f"{NVIDIA_BASE_URL}/models", headers=headers)
                if resp.status_code == 200:
                    data = resp.json().get("data", [])
                    nemotron_models = [m for m in data if "nemotron" in m["id"].lower()]
                    if nemotron_models:
                        return [
                            ModelInfo(
                                id=m["id"],
                                name=f"NVIDIA {m['id']}",
                                provider="nvidia",
                                context_length=131072
                            )
                            for m in nemotron_models
                        ]
        except Exception as e:
            logger.error(f"NVIDIA models list error: {e}")

        return [
            ModelInfo(id="nvidia/nemotron-3.5-lightning-30b-a3b", name="NVIDIA Nemotron 3.5 Lightning", provider="nvidia", context_length=131072),
            ModelInfo(id="nvidia/llama-3.1-nemotron-70b-instruct", name="NVIDIA Llama 3.1 Nemotron 70B", provider="nvidia", context_length=131072),
            ModelInfo(id="nvidia/nemotron-4-340b-instruct", name="NVIDIA Nemotron 4 340B", provider="nvidia", context_length=131072)
        ]

    async def generate(self, request: LLMRequest) -> LLMResponse:
        if not self.api_key:
            raise ValueError("NVIDIA API key not configured")

        model = request.model or "nvidia/nemotron-3.5-lightning-30b-a3b"
        messages = []
        if request.system_prompt:
            messages.append({"role": "system", "content": request.system_prompt})
        messages.append({"role": "user", "content": request.prompt})

        headers = {"Authorization": f"Bearer {self.api_key}", "Content-Type": "application/json"}
        payload = {"model": model, "messages": messages, "temperature": request.temperature}

        async with httpx.AsyncClient(timeout=20.0) as client:
            resp = await client.post(f"{NVIDIA_BASE_URL}/chat/completions", headers=headers, json=payload)
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
                provider="nvidia",
                model=model,
                raw_response=data
            )

    async def stream(self, request: LLMRequest) -> AsyncGenerator[str, None]:
        res = await self.generate(request)
        yield res.text

    async def health_check(self) -> bool:
        return await self.validate_key()
