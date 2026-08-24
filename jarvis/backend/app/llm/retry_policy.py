"""
Retry Policy for transient LLM errors.
"""

import asyncio
import logging
from collections.abc import Awaitable, Callable
from typing import Any

logger = logging.getLogger(__name__)

class RetryPolicy:
    def __init__(self, max_retries: int = 2, base_delay_seconds: float = 0.5) -> None:
        self.max_retries = max_retries
        self.base_delay_seconds = base_delay_seconds

    async def execute(self, fn: Callable[[], Awaitable[Any]]) -> Any:
        last_exception = None
        for attempt in range(self.max_retries + 1):
            try:
                return await fn()
            except Exception as e:
                last_exception = e
                if attempt < self.max_retries:
                    delay = self.base_delay_seconds * (2 ** attempt)
                    await asyncio.sleep(delay)
        raise last_exception


retry_policy = RetryPolicy()
