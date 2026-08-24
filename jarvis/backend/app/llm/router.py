"""
Provider Router for Jarvis multi-provider failover.
"""

import logging
from collections.abc import Awaitable, Callable
from typing import Any

from app.llm.circuit_breaker import CircuitBreaker

logger = logging.getLogger(__name__)


class ProviderRouter:
    def __init__(self) -> None:
        self.circuit_breakers: dict[str, CircuitBreaker] = {
            "nvidia": CircuitBreaker(),
            "groq": CircuitBreaker(),
            "openrouter": CircuitBreaker(),
            "gemini": CircuitBreaker(),
            "ollama": CircuitBreaker(),
        }

    async def execute_with_failover(
        self,
        providers_chain: list,
        fallback_fn: Callable[[], Awaitable[Any]]
    ) -> Any:
        for provider_name, fn in providers_chain:
            cb = self.circuit_breakers.setdefault(provider_name, CircuitBreaker())
            if cb.allow_execution():
                try:
                    result = await fn()
                    cb.record_success()
                    return result
                except Exception as e:
                    logger.warning(f"Provider '{provider_name}' execution failed: {e}")
                    cb.record_failure()

        return await fallback_fn()


provider_router = ProviderRouter()
