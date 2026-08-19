"""
LLM Gateway facade for Jarvis reasoning requests.
"""

import logging
from typing import Dict, Any, Optional

from app.llm.base import LLMRequest, LLMResponse
from app.llm.registry import llm_registry
from app.llm.router import provider_router
from app.llm.retry_policy import retry_policy

logger = logging.getLogger(__name__)


class LLMGateway:
    async def generate_reasoning(
        self,
        prompt: str,
        system_prompt: Optional[str] = None,
        context: Optional[Dict[str, Any]] = None,
        requested_provider: Optional[str] = None,
        requested_model: Optional[str] = None,
    ) -> LLMResponse:
        req = LLMRequest(
            prompt=prompt,
            system_prompt=system_prompt,
            model=requested_model,
        )

        active_provider = await llm_registry.get_active_provider()
        chain = []
        if requested_provider and requested_provider in llm_registry._providers:
            p = llm_registry._providers[requested_provider]
            chain.append((requested_provider, lambda: retry_policy.execute(lambda: p.generate(req))))
        elif active_provider:
            chain.append((active_provider.provider_name, lambda: retry_policy.execute(lambda: active_provider.generate(req))))

        for p_name, p in llm_registry._providers.items():
            if not any(c[0] == p_name for c in chain):
                chain.append((p_name, lambda p=p: retry_policy.execute(lambda: p.generate(req))))

        async def _local_fallback() -> LLMResponse:
            return LLMResponse(
                text="Jarvis local rule response",
                action="unknown",
                parameters={},
                confidence=0.0,
                provider="fallback",
                model="local-rule",
            )

        return await provider_router.execute_with_failover(chain, _local_fallback)


llm_gateway = LLMGateway()
