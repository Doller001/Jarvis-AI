"""
Base interface for Jarvis LLM Provider Adapters.
"""

import json
import re
from abc import ABC, abstractmethod
from collections.abc import AsyncGenerator
from typing import Any

from pydantic import BaseModel, Field


def extract_action_and_params(content: str) -> tuple[str | None, dict[str, Any], float]:
    """
    Extracts action, parameters, and confidence from LLM response text,
    supporting raw JSON, markdown code fences (```json ... ```), and embedded JSON.
    """
    if not content:
        return None, {}, 0.0

    text = content.strip()

    # Check for markdown code blocks (```json ... ``` or ``` ... ```)
    match = re.search(r"```(?:json)?\s*(\{.*?\})\s*```", text, re.DOTALL)
    if match:
        try:
            parsed = json.loads(match.group(1))
            if isinstance(parsed, dict) and "action" in parsed:
                return parsed.get("action"), parsed.get("parameters", {}), float(parsed.get("confidence", 0.95))
        except Exception:
            pass

    # Check for raw or embedded JSON object with "action"
    match = re.search(r"\{[^{}]*\"action\"\s*:\s*\"[^\"]+\"[^{}]*\}", text, re.DOTALL)
    if match:
        try:
            parsed = json.loads(match.group(0))
            if isinstance(parsed, dict) and "action" in parsed:
                return parsed.get("action"), parsed.get("parameters", {}), float(parsed.get("confidence", 0.95))
        except Exception:
            pass

    if text.startswith("{") and text.endswith("}"):
        try:
            parsed = json.loads(text)
            if isinstance(parsed, dict) and "action" in parsed:
                return parsed.get("action"), parsed.get("parameters", {}), float(parsed.get("confidence", 0.95))
        except Exception:
            pass

    return None, {}, 0.0


class ModelInfo(BaseModel):
    id: str
    name: str
    provider: str
    context_length: int = 4096
    supports_streaming: bool = True
    supports_tools: bool = True


class LLMRequest(BaseModel):
    prompt: str
    system_prompt: str | None = None
    model: str | None = None
    temperature: float = 0.7
    max_tokens: int = 1024


class LLMResponse(BaseModel):
    text: str
    action: str | None = None
    parameters: dict[str, Any] = Field(default_factory=dict)
    confidence: float = 1.0
    provider: str = "unknown"
    model: str = "unknown"
    raw_response: dict[str, Any] | None = None


class LLMProvider(ABC):
    def __init__(self, provider_name: str, api_key: str | None = None):
        self.provider_name = provider_name
        self.api_key = api_key

    @abstractmethod
    async def validate_key(self) -> bool:
        pass

    @abstractmethod
    async def list_models(self) -> list[ModelInfo]:
        pass

    @abstractmethod
    async def generate(self, request: LLMRequest) -> LLMResponse:
        pass

    @abstractmethod
    async def stream(self, request: LLMRequest) -> AsyncGenerator[str, None]:
        pass

    @abstractmethod
    async def health_check(self) -> bool:
        pass
