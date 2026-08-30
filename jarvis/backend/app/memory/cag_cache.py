"""
Context-Aware Generator (CAG) Fast Cache Engine.

Provides an in-memory, thread-safe cache for context and sensory-aware query responses,
featuring TTL expiration, SHA-256 hash indexing with sensory fingerprinting, FIFO/LRU eviction,
and flexible pattern-based invalidation.
"""

from collections import OrderedDict
import fnmatch
import hashlib
import threading
import time
from typing import Any, Optional


class CAGCache:
    """
    In-memory fast cache engine for Context-Aware Generation.
    """

    def __init__(self, max_entries: int = 1000) -> None:
        self.max_entries = max_entries
        self._cache: OrderedDict[str, dict[str, Any]] = OrderedDict()
        self._lock = threading.RLock()

    def compute_hash(self, text: str, sensory_fingerprint: Optional[str] = None) -> str:
        """
        Normalizes text (strip and lowercase), optionally appends sensory fingerprint,
        and computes SHA-256 hex digest.
        """
        normalized = text.strip().lower()
        if sensory_fingerprint:
            normalized = f"{normalized}::{sensory_fingerprint}"
        return hashlib.sha256(normalized.encode("utf-8")).hexdigest()

    def get(self, intent_hash: str) -> Optional[dict[str, Any]]:
        """
        Retrieves cached response if present and not expired.
        Evicts expired entries on the fly.
        """
        with self._lock:
            if intent_hash not in self._cache:
                return None

            entry = self._cache[intent_hash]
            expires_at = entry.get("expires_at")
            if expires_at is not None and time.time() > expires_at:
                del self._cache[intent_hash]
                return None

            return entry.get("response")

    def set(
        self,
        intent_hash: str,
        response: dict[str, Any],
        ttl_seconds: Optional[int] = 300,
    ) -> None:
        """
        Inserts or updates an entry with an expiration timestamp,
        evicting the oldest entries if max_entries is exceeded.
        """
        with self._lock:
            expires_at = (
                time.time() + ttl_seconds if ttl_seconds is not None else None
            )

            if intent_hash in self._cache:
                self._cache[intent_hash] = {
                    "response": response,
                    "expires_at": expires_at,
                }
                self._cache.move_to_end(intent_hash)
            else:
                while len(self._cache) >= self.max_entries:
                    self._cache.popitem(last=False)
                self._cache[intent_hash] = {
                    "response": response,
                    "expires_at": expires_at,
                }

    def invalidate(self, pattern: Optional[str] = None) -> None:
        """
        Clears all cache entries if pattern is None,
        otherwise evicts entries matching the pattern.
        """
        with self._lock:
            if pattern is None:
                self._cache.clear()
            else:
                keys_to_delete = [
                    k
                    for k in self._cache.keys()
                    if fnmatch.fnmatch(k, pattern) or pattern in k
                ]
                for k in keys_to_delete:
                    self._cache.pop(k, None)


cag_cache = CAGCache()
