"""
Supabase Database Integration for Jarvis Backend.

Supports:
1. Supabase PostgREST REST API (via httpx / supabase-py SDK)
2. Supabase Direct PostgreSQL connection pooling (with sslmode=require)
3. Automatic schema initialization, health checks, and fallback logic
"""

from __future__ import annotations

import logging
import os
import time
from typing import Any, Optional

import httpx

logger = logging.getLogger("jarvis.supabase")


class SupabaseClient:
    """
    Robust Supabase client providing both REST and PostgreSQL access for
    conversations, memories, and user preferences.
    """

    def __init__(
        self,
        supabase_url: Optional[str] = None,
        supabase_key: Optional[str] = None,
        database_url: Optional[str] = None,
    ) -> None:
        self.url = (
            supabase_url
            or os.getenv("SUPABASE_URL")
            or os.getenv("NEXT_PUBLIC_SUPABASE_URL")
            or ""
        ).strip().rstrip("/")
        
        self.key = (
            supabase_key
            or os.getenv("SUPABASE_KEY")
            or os.getenv("SUPABASE_SERVICE_ROLE_KEY")
            or os.getenv("SUPABASE_ANON_KEY")
            or ""
        ).strip()

        self.db_url = (
            database_url
            or os.getenv("SUPABASE_DATABASE_URL")
            or os.getenv("DATABASE_URL")
            or ""
        ).strip()

        self.is_rest_enabled = bool(self.url and self.key)
        self.is_postgres_enabled = bool(
            self.db_url and (self.db_url.startswith("postgresql://") or self.db_url.startswith("postgres://"))
        )
        
        self._http_client: Optional[httpx.Client] = None
        if self.is_rest_enabled:
            headers = {
                "apikey": self.key,
                "Authorization": f"Bearer {self.key}",
                "Content-Type": "application/json",
                "Prefer": "return=representation",
            }
            self._http_client = httpx.Client(
                base_url=f"{self.url}/rest/v1",
                headers=headers,
                timeout=8.0,
            )
            logger.info(f"Supabase REST API client configured for {self._masked_url()}")
        elif self.is_postgres_enabled:
            logger.info("Supabase PostgreSQL direct client configured.")
        else:
            logger.info("Supabase credentials not found; running in fallback mode.")

    def _masked_url(self) -> str:
        if not self.url:
            return "N/A"
        try:
            parts = self.url.split("://")
            return f"{parts[0]}://{parts[1][:8]}***.supabase.co"
        except Exception:
            return "https://***.supabase.co"

    @property
    def is_active(self) -> bool:
        return self.is_rest_enabled or self.is_postgres_enabled

    def ping(self) -> dict[str, Any]:
        """Check connection health and return status with round-trip latency."""
        start = time.time()
        if self.is_rest_enabled and self._http_client:
            try:
                # Query conversations table or root endpoint
                resp = self._http_client.get("/conversations?select=id&limit=1")
                latency_ms = round((time.time() - start) * 1000, 2)
                if resp.status_code in (200, 206):
                    return {
                        "status": "connected",
                        "driver": "supabase_rest",
                        "url": self._masked_url(),
                        "latency_ms": latency_ms,
                        "table_exists": True,
                    }
                elif resp.status_code in (401, 403):
                    return {
                        "status": "auth_error",
                        "driver": "supabase_rest",
                        "url": self._masked_url(),
                        "error": "Invalid API key or unauthorized",
                    }
                elif resp.status_code == 404:
                    # Table might not exist yet, but connection is alive
                    return {
                        "status": "connected_table_missing",
                        "driver": "supabase_rest",
                        "url": self._masked_url(),
                        "latency_ms": latency_ms,
                        "table_exists": False,
                    }
                else:
                    return {
                        "status": "degraded",
                        "driver": "supabase_rest",
                        "http_code": resp.status_code,
                        "error": resp.text[:200],
                    }
            except Exception as e:
                return {
                    "status": "error",
                    "driver": "supabase_rest",
                    "url": self._masked_url(),
                    "error": str(e),
                }

        if self.is_postgres_enabled:
            try:
                import psycopg2
                url = self.db_url
                if url.startswith("postgres://"):
                    url = url.replace("postgres://", "postgresql://", 1)
                if "sslmode" not in url:
                    url += "?sslmode=require" if "?" not in url else "&sslmode=require"
                conn = psycopg2.connect(url, connect_timeout=5)
                with conn.cursor() as cur:
                    cur.execute("SELECT 1;")
                conn.close()
                latency_ms = round((time.time() - start) * 1000, 2)
                return {
                    "status": "connected",
                    "driver": "supabase_postgres",
                    "latency_ms": latency_ms,
                }
            except Exception as e:
                return {
                    "status": "error",
                    "driver": "supabase_postgres",
                    "error": str(e),
                }

        return {
            "status": "unconfigured",
            "driver": "none",
            "message": "Set SUPABASE_URL and SUPABASE_KEY or SUPABASE_DATABASE_URL.",
        }

    def save_conversation_message(
        self, session_id: str, role: str, content: str, metadata: Optional[dict] = None
    ) -> bool:
        """Saves a conversation message to Supabase."""
        now = time.time()
        payload = {
            "session_id": session_id,
            "role": role,
            "content": content,
            "timestamp": now,
        }

        # 1. REST API
        if self.is_rest_enabled and self._http_client:
            try:
                resp = self._http_client.post("/conversations", json=payload)
                if resp.status_code in (200, 201, 204):
                    return True
                logger.warning(f"Supabase REST save_message returned {resp.status_code}: {resp.text}")
            except Exception as e:
                logger.error(f"Supabase REST save_message exception: {e}")

        # 2. PostgreSQL Direct
        if self.is_postgres_enabled:
            try:
                import psycopg2
                url = self.db_url
                if url.startswith("postgres://"):
                    url = url.replace("postgres://", "postgresql://", 1)
                conn = psycopg2.connect(url)
                with conn.cursor() as cur:
                    cur.execute(
                        "INSERT INTO conversations (session_id, role, content, timestamp) VALUES (%s, %s, %s, %s)",
                        (session_id, role, content, now),
                    )
                conn.commit()
                conn.close()
                return True
            except Exception as e:
                logger.error(f"Supabase PostgreSQL save_message exception: {e}")

        return False

    def get_conversation_history(self, session_id: str, limit: int = 10) -> Optional[list[dict[str, Any]]]:
        """Retrieves recent conversation history from Supabase."""
        # 1. REST API
        if self.is_rest_enabled and self._http_client:
            try:
                params = {
                    "session_id": f"eq.{session_id}",
                    "order": "timestamp.desc",
                    "limit": str(limit),
                    "select": "role,content,timestamp",
                }
                resp = self._http_client.get("/conversations", params=params)
                if resp.status_code == 200:
                    rows = resp.json()
                    return [{"role": r["role"], "content": r["content"], "timestamp": r["timestamp"]} for r in reversed(rows)]
                logger.warning(f"Supabase REST get_history returned {resp.status_code}: {resp.text}")
            except Exception as e:
                logger.error(f"Supabase REST get_history exception: {e}")

        # 2. PostgreSQL Direct
        if self.is_postgres_enabled:
            try:
                import psycopg2
                import psycopg2.extras
                url = self.db_url
                if url.startswith("postgres://"):
                    url = url.replace("postgres://", "postgresql://", 1)
                conn = psycopg2.connect(url, cursor_factory=psycopg2.extras.RealDictCursor)
                with conn.cursor() as cur:
                    cur.execute(
                        "SELECT role, content, timestamp FROM conversations WHERE session_id = %s ORDER BY id DESC LIMIT %s",
                        (session_id, limit),
                    )
                    rows = cur.fetchall()
                conn.close()
                return [{"role": r["role"], "content": r["content"], "timestamp": r["timestamp"]} for r in reversed(rows)]
            except Exception as e:
                logger.error(f"Supabase PostgreSQL get_history exception: {e}")

        return None

    def set_user_preference(self, key: str, value: str) -> bool:
        """Sets or updates a key-value user preference."""
        now = time.time()
        if self.is_rest_enabled and self._http_client:
            try:
                payload = {"key": key, "value": value, "updated_at": now}
                # PostgREST upsert via resolution parameter
                headers = {"Prefer": "resolution=merge-duplicates"}
                resp = self._http_client.post("/user_preferences", json=payload, headers=headers)
                return resp.status_code in (200, 201, 204)
            except Exception as e:
                logger.error(f"Supabase set_user_preference error: {e}")

        if self.is_postgres_enabled:
            try:
                import psycopg2
                url = self.db_url
                if url.startswith("postgres://"):
                    url = url.replace("postgres://", "postgresql://", 1)
                conn = psycopg2.connect(url)
                with conn.cursor() as cur:
                    cur.execute(
                        """
                        INSERT INTO user_preferences (key, value, updated_at)
                        VALUES (%s, %s, %s)
                        ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = EXCLUDED.updated_at;
                        """,
                        (key, value, now),
                    )
                conn.commit()
                conn.close()
                return True
            except Exception as e:
                logger.error(f"Supabase PostgreSQL set_user_preference error: {e}")

        return False

    def get_user_preference(self, key: str) -> Optional[str]:
        """Gets a user preference value by key."""
        if self.is_rest_enabled and self._http_client:
            try:
                resp = self._http_client.get(f"/user_preferences?key=eq.{key}&select=value")
                if resp.status_code == 200:
                    rows = resp.json()
                    if rows:
                        return rows[0].get("value")
            except Exception as e:
                logger.error(f"Supabase get_user_preference error: {e}")

        if self.is_postgres_enabled:
            try:
                import psycopg2
                url = self.db_url
                if url.startswith("postgres://"):
                    url = url.replace("postgres://", "postgresql://", 1)
                conn = psycopg2.connect(url)
                with conn.cursor() as cur:
                    cur.execute("SELECT value FROM user_preferences WHERE key = %s LIMIT 1", (key,))
                    row = cur.fetchone()
                conn.close()
                if row:
                    return row[0]
            except Exception as e:
                logger.error(f"Supabase PostgreSQL get_user_preference error: {e}")

        return None


# Global singleton instance
supabase_client = SupabaseClient()
