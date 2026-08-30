"""
Tests for Context-Aware Generator (CAG) Fast Cache Engine.
"""

import time
import hashlib
from app.memory.cag_cache import CAGCache, cag_cache


def test_singleton_instance():
    assert isinstance(cag_cache, CAGCache)


def test_compute_hash_normalization():
    cache = CAGCache()
    # Test case insensitivity and whitespace stripping
    hash1 = cache.compute_hash("  Turn On Lights  ")
    hash2 = cache.compute_hash("turn on lights")
    assert hash1 == hash2

    expected_raw = "turn on lights".encode("utf-8")
    expected_hash = hashlib.sha256(expected_raw).hexdigest()
    assert hash1 == expected_hash


def test_compute_hash_with_sensory_fingerprint():
    cache = CAGCache()
    query = "turn on the lights"
    hash_no_fp = cache.compute_hash(query)
    hash_fp1 = cache.compute_hash(query, sensory_fingerprint="room:kitchen")
    hash_fp2 = cache.compute_hash(query, sensory_fingerprint="room:bedroom")

    assert hash_no_fp != hash_fp1
    assert hash_fp1 != hash_fp2

    expected_raw = f"{query}::room:kitchen".encode("utf-8")
    expected_hash = hashlib.sha256(expected_raw).hexdigest()
    assert hash_fp1 == expected_hash


def test_cache_hit_and_miss():
    cache = CAGCache()
    key = cache.compute_hash("what is the weather")
    
    # Cache miss
    assert cache.get(key) is None

    # Cache set & hit
    response_payload = {"reply": "It is sunny", "confidence": 0.99}
    cache.set(key, response_payload, ttl_seconds=60)
    
    cached = cache.get(key)
    assert cached is not None
    assert cached == response_payload


def test_ttl_expiration(monkeypatch):
    cache = CAGCache()
    key = cache.compute_hash("temporary fact")
    response_payload = {"reply": "Expires quickly"}

    current_time = 1000.0
    monkeypatch.setattr(time, "time", lambda: current_time)

    cache.set(key, response_payload, ttl_seconds=10)
    assert cache.get(key) == response_payload

    # Advance time within TTL
    current_time = 1009.0
    assert cache.get(key) == response_payload

    # Advance time beyond TTL
    current_time = 1011.0
    assert cache.get(key) is None
    # Verify it was evicted/cleaned up
    assert key not in cache._cache


def test_full_invalidation():
    cache = CAGCache()
    k1 = cache.compute_hash("query 1")
    k2 = cache.compute_hash("query 2")

    cache.set(k1, {"result": 1})
    cache.set(k2, {"result": 2})

    assert cache.get(k1) is not None
    assert cache.get(k2) is not None

    cache.invalidate()

    assert cache.get(k1) is None
    assert cache.get(k2) is None


def test_pattern_invalidation():
    cache = CAGCache()
    k1 = "weather_today"
    k2 = "weather_tomorrow"
    k3 = "news_general"

    cache.set(k1, {"data": "sunny"})
    cache.set(k2, {"data": "rainy"})
    cache.set(k3, {"data": "headlines"})

    # Pattern match invalidation
    cache.invalidate(pattern="weather_*")
    assert cache.get(k1) is None
    assert cache.get(k2) is None
    assert cache.get(k3) is not None

    # Invalidate by substring pattern
    cache.invalidate(pattern="news")
    assert cache.get(k3) is None


def test_max_entries_eviction():
    cache = CAGCache(max_entries=3)
    
    cache.set("k1", {"val": 1})
    cache.set("k2", {"val": 2})
    cache.set("k3", {"val": 3})

    assert len(cache._cache) == 3
    assert cache.get("k1") is not None

    # Adding a 4th element should evict the oldest element ("k1")
    cache.set("k4", {"val": 4})
    assert len(cache._cache) == 3
    assert cache.get("k1") is None
    assert cache.get("k2") is not None
    assert cache.get("k3") is not None
    assert cache.get("k4") is not None
