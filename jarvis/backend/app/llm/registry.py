"""
Jarvis LLM Provider Registry and Dynamic Model Discovery.
"""

import logging
from typing import Dict, List, Any, Optional
from pydantic import BaseModel

from app.llm.base import LLMProvider, ModelInfo
from app.llm.providers.groq import GroqProvider
from app.llm.providers.openrouter import OpenRouterProvider
from app.llm.providers.gemini import GeminiProvider
from app.llm.providers.ollama import OllamaProvider

logger = logging.getLogger(__name__)


class ProviderStatus(BaseModel):
    provider: str
    authenticated: bool
    healthy: bool
    models: List[ModelInfo] = []


class LLMRegistry:
    def __init__(self) -> None:
        self._providers: Dict[str, LLMProvider] = {}
        self._active_provider_name: Optional[str] = None
        self._active_model_id: Optional[str] = None
        self.reload_providers()

    def reload_providers(self) -> None:
        self._providers = {
            "groq": GroqProvider(),
            "openrouter": OpenRouterProvider(),
            "gemini": GeminiProvider(),
            "ollama": OllamaProvider(),
        }

    def register_provider(self, name: str, provider: LLMProvider) -> None:
        self._providers[name] = provider

    async def discover_available_providers(self) -> List[ProviderStatus]:
        statuses = []
        for name, provider in self._providers.items():
            try:
                is_auth = await provider.validate_key()
                if not is_auth:
                    continue  # Hide unauthenticated providers

                is_healthy = await provider.health_check()
                models = await provider.list_models()

                statuses.append(
                    ProviderStatus(
                        provider=name,
                        authenticated=is_auth,
                        healthy=is_healthy,
                        models=models
                    )
                )
            except Exception as e:
                logger.warning(f"Error discovering provider '{name}': {e}")
                continue

        return statuses

    async def get_active_provider(self) -> Optional[LLMProvider]:
        if self._active_provider_name and self._active_provider_name in self._providers:
            provider = self._providers[self._active_provider_name]
            if await provider.validate_key():
                return provider

        for name, provider in self._providers.items():
            if await provider.validate_key():
                self._active_provider_name = name
                return provider

        return None

    def set_active_provider_and_model(self, provider_name: str, model_id: Optional[str] = None) -> bool:
        if provider_name in self._providers:
            self._active_provider_name = provider_name
            self._active_model_id = model_id
            logger.info(f"Switched active LLM provider to '{provider_name}' (model: '{model_id}')")
            return True
        return False

    def get_active_selection(self) -> Dict[str, Optional[str]]:
        return {
            "provider": self._active_provider_name,
            "model": self._active_model_id
        }


llm_registry = LLMRegistry()
