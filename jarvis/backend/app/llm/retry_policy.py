"""
Retry Policy for transient LLM errors with error classification and exponential backoff with jitter.
"""

import asyncio
import logging
import random
from collections.abc import Awaitable, Callable
from typing import Any
import httpx

logger = logging.getLogger(__name__)

NON_RETRYABLE_STATUS_CODES = {400, 401, 403, 404, 422}


def is_retryable_exception(exc: Exception) -> bool:
    if isinstance(exc, (asyncio.TimeoutError, TimeoutError, ConnectionError)):
        return True
    if isinstance(exc, httpx.HTTPStatusError):
        code = exc.response.status_code
        if code in NON_RETRYABLE_STATUS_CODES:
            return False
        return code in {408, 425, 429, 500, 502, 503, 504}
    if isinstance(exc, (httpx.ConnectError, httpx.ReadTimeout, httpx.WriteTimeout, httpx.PoolTimeout)):
        return True
    # Default: do not retry unknown logical/validation exceptions
    msg = str(exc).lower()
    if "api key" in msg or "unauthorized" in msg or "invalid model" in msg or "forbidden" in msg:
        return False
    return True


class RetryPolicy:
    def __init__(self, max_retries: int = 2, base_delay_seconds: float = 0.25) -> None:
        self.max_retries = max_retries
        self.base_delay_seconds = base_delay_seconds

    async def execute(self, fn: Callable[[], Awaitable[Any]]) -> Any:
        last_exception = None
        for attempt in range(self.max_retries + 1):
            try:
                return await fn()
            except Exception as e:
                last_exception = e
                if not is_retryable_exception(e):
                    logger.info(f"[RETRY_POLICY] Permanent error detected ({type(e).__name__}: {e}) — skipping retries.")
                    raise e
                if attempt < self.max_retries:
                    # Exponential backoff with jitter: base * 2^attempt + jitter
                    jitter = random.uniform(0.05, 0.15)
                    delay = min(self.base_delay_seconds * (2 ** attempt) + jitter, 2.0)
                    logger.info(f"[RETRY_POLICY] Transient error (attempt {attempt + 1}/{self.max_retries}). Retrying in {delay:.2f}s...")
                    await asyncio.sleep(delay)
        if last_exception:
            raise last_exception


retry_policy = RetryPolicy()
