"""
Redis-backed Connection Registry for Jarvis.
Supports multi-instance backend deployments with shared WebSocket state.
"""

import json
import logging
import os
import time
from dataclasses import dataclass, field
from typing import Any

logger = logging.getLogger("jarvis.realtime.redis")

REDIS_URL = os.getenv("REDIS_URL", "")


@dataclass
class DeviceConnection:
    device_id: str
    session_id: str
    backend_instance: str
    connected_at: float
    last_seen: float
    status: str = "connected"  # connected, disconnected


class RedisConnectionRegistry:
    """
    Redis-backed connection registry for multi-instance support.
    Falls back to in-memory if Redis is unavailable.
    """

    def __init__(self, redis_url: str | None = None):
        self._redis_url = redis_url or REDIS_URL
        self._redis = None
        self._memory_cache: dict[str, DeviceConnection] = {}
        self._connected = False

        if self._redis_url:
            try:
                import redis.asyncio as aioredis
                self._redis = aioredis.from_url(
                    self._redis_url,
                    decode_responses=True,
                    socket_connect_timeout=5,
                    socket_timeout=5,
                )
                self._connected = True
                logger.info("Redis connection registry initialized")
            except Exception as e:
                logger.warning(f"Failed to connect to Redis: {e}. Using in-memory fallback.")
                self._redis = None
                self._connected = False

    async def register(
        self,
        device_id: str,
        session_id: str,
        backend_instance: str,
        ttl_seconds: int = 300,
    ) -> None:
        now = time.time()
        connection = DeviceConnection(
            device_id=device_id,
            session_id=session_id,
            backend_instance=backend_instance,
            connected_at=now,
            last_seen=now,
        )

        if self._redis:
            try:
                key = f"jarvis:device:{device_id}"
                data = {
                    "device_id": device_id,
                    "session_id": session_id,
                    "backend_instance": backend_instance,
                    "connected_at": str(now),
                    "last_seen": str(now),
                    "status": "connected",
                }
                await self._redis.hset(key, mapping=data)
                await self._redis.expire(key, ttl_seconds)
                logger.info(f"Registered device {device_id} in Redis")
            except Exception as e:
                logger.error(f"Redis register failed: {e}")

        self._memory_cache[device_id] = connection

    async def get_connection(self, device_id: str) -> DeviceConnection | None:
        if self._redis:
            try:
                key = f"jarvis:device:{device_id}"
                data = await self._redis.hgetall(key)
                if data:
                    return DeviceConnection(
                        device_id=data["device_id"],
                        session_id=data["session_id"],
                        backend_instance=data["backend_instance"],
                        connected_at=float(data["connected_at"]),
                        last_seen=float(data["last_seen"]),
                        status=data.get("status", "connected"),
                    )
            except Exception as e:
                logger.error(f"Redis get_connection failed: {e}")

        return self._memory_cache.get(device_id)

    async def disconnect(self, device_id: str) -> None:
        if self._redis:
            try:
                key = f"jarvis:device:{device_id}"
                await self._redis.hset(key, mapping={"status": "disconnected"})
            except Exception as e:
                logger.error(f"Redis disconnect failed: {e}")

        if device_id in self._memory_cache:
            self._memory_cache[device_id].status = "disconnected"

    async def touch(self, device_id: str) -> None:
        now = time.time()
        if self._redis:
            try:
                key = f"jarvis:device:{device_id}"
                await self._redis.hset(key, mapping={"last_seen": str(now)})
            except Exception as e:
                logger.error(f"Redis touch failed: {e}")

        if device_id in self._memory_cache:
            self._memory_cache[device_id].last_seen = now

    async def list_active(self) -> list[DeviceConnection]:
        if self._redis:
            try:
                connections = []
                cursor = 0
                while True:
                    cursor, keys = await self._redis.scan(
                        cursor=cursor, match="jarvis:device:*", count=100
                    )
                    for key in keys:
                        data = await self._redis.hgetall(key)
                        if data and data.get("status") == "connected":
                            connections.append(DeviceConnection(
                                device_id=data["device_id"],
                                session_id=data["session_id"],
                                backend_instance=data["backend_instance"],
                                connected_at=float(data["connected_at"]),
                                last_seen=float(data["last_seen"]),
                                status=data["status"],
                            ))
                    if cursor == 0:
                        break
                return connections
            except Exception as e:
                logger.error(f"Redis list_active failed: {e}")

        return [c for c in self._memory_cache.values() if c.status == "connected"]

    async def cleanup_stale(self, max_age_seconds: int = 600) -> int:
        now = time.time()
        removed = 0

        if self._redis:
            try:
                cursor = 0
                while True:
                    cursor, keys = await self._redis.scan(
                        cursor=cursor, match="jarvis:device:*", count=100
                    )
                    for key in keys:
                        data = await self._redis.hgetall(key)
                        if data:
                            last_seen = float(data.get("last_seen", 0))
                            if now - last_seen > max_age_seconds:
                                await self._redis.delete(key)
                                removed += 1
                    if cursor == 0:
                        break
            except Exception as e:
                logger.error(f"Redis cleanup failed: {e}")

        stale_devices = [
            did for did, conn in self._memory_cache.items()
            if now - conn.last_seen > max_age_seconds
        ]
        for did in stale_devices:
            del self._memory_cache[did]
            removed += 1

        return removed

    async def close(self) -> None:
        if self._redis:
            await self._redis.close()


# Global singleton
redis_registry = RedisConnectionRegistry()
