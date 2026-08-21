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
            chain.append((requested_provider, lambda p=p: retry_policy.execute(lambda p=p: p.generate(req))))
        elif active_provider:
            chain.append((active_provider.provider_name, lambda p=active_provider: retry_policy.execute(lambda p=active_provider: p.generate(req))))

        for p_name, p in llm_registry._providers.items():
            if not any(c[0] == p_name for c in chain):
                chain.append((p_name, lambda p=p: retry_policy.execute(lambda p=p: p.generate(req))))

        async def _local_fallback() -> LLMResponse:
            p = prompt.lower().strip()
            if any(w in p for w in ["hello", "hey", "hi", "suno", "namaste"]):
                ans = "Hello! I am Jarvis. How can I assist you today?"
            elif any(w in p for w in ["who are you", "what is your name", "aap kaun ho", "tum kaun ho"]):
                ans = "I am Jarvis, your personal voice and device automation assistant."
            elif any(w in p for w in ["how are you", "kaise ho", "kya haal"]):
                ans = "All systems are operating at peak performance! Ready for your command."
            elif any(w in p for w in ["what can you do", "kya kar sakte ho", "help", "features"]):
                ans = "I can control device hardware (Torch, Wi-Fi, Volume), launch apps, check storage/battery, manage memory, and assist you with daily tasks."
            elif any(w in p for w in ["thank", "dhanyawad", "shukriya"]):
                ans = "You are most welcome! Always at your service."
            elif any(w in p for w in ["bye", "good night", "alvida"]):
                ans = "Goodbye! Let me know whenever you need assistance."
            else:
                ans = f"Processed '{prompt}'. Connected to Jarvis Cloud Gateway."

            return LLMResponse(
                text=ans,
                action="answer",
                parameters={"query": prompt},
                confidence=0.95,
                provider="fallback",
                model="local-rule",
            )

        return await provider_router.execute_with_failover(chain, _local_fallback)


llm_gateway = LLMGateway()
