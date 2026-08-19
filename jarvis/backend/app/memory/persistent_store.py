"""
Persistent Store for Jarvis supporting Supabase PostgreSQL and SQLite.
"""

import os
import time
import logging
from typing import Dict, Any, List, Optional

logger = logging.getLogger(__name__)

DB_PATH = os.getenv("JARVIS_DB_PATH", "jarvis_memory.db")
DATABASE_URL = os.getenv("DATABASE_URL") or os.getenv("SUPABASE_DATABASE_URL")


class PersistentStore:
    def __init__(self) -> None:
        self.db_url = DATABASE_URL
        self.db_path = DB_PATH
        self.is_postgres = False

        if self.db_url and (self.db_url.startswith("postgresql://") or self.db_url.startswith("postgres://")):
            self.is_postgres = True
            logger.info("Initializing Jarvis Persistent Store with Supabase PostgreSQL connection.")
        else:
            logger.info(f"Initializing Jarvis Persistent Store with SQLite at '{self.db_path}'.")

        self._init_db()

    def _get_sqlite_connection(self):
        import sqlite3
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        return conn

    def _get_postgres_connection(self):
        try:
            import psycopg2
            import psycopg2.extras
            url = self.db_url
            if url.startswith("postgres://"):
                url = url.replace("postgres://", "postgresql://", 1)
            conn = psycopg2.connect(url, cursor_factory=psycopg2.extras.RealDictCursor)
            return conn
        except Exception as e:
            logger.warning(f"PostgreSQL connection error ({e}); falling back to SQLite for local development.")
            self.is_postgres = False
            return self._get_sqlite_connection()

    def _init_db(self) -> None:
        if self.is_postgres:
            try:
                conn = self._get_postgres_connection()
                with conn.cursor() as cur:
                    cur.execute("""
                        CREATE TABLE IF NOT EXISTS conversations (
                            id SERIAL PRIMARY KEY,
                            session_id VARCHAR(255) NOT NULL,
                            role VARCHAR(50) NOT NULL,
                            content TEXT NOT NULL,
                            timestamp DOUBLE PRECISION NOT NULL
                        );
                        CREATE TABLE IF NOT EXISTS user_preferences (
                            key VARCHAR(255) PRIMARY KEY,
                            value TEXT NOT NULL,
                            updated_at DOUBLE PRECISION NOT NULL
                        );
                    """)
                conn.commit()
                conn.close()
                return
            except Exception as e:
                logger.error(f"Error initializing Supabase PostgreSQL tables: {e}; falling back to SQLite.")
                self.is_postgres = False

        # SQLite fallback
        conn = self._get_sqlite_connection()
        with conn:
            conn.executescript("""
                CREATE TABLE IF NOT EXISTS conversations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    session_id TEXT NOT NULL,
                    role TEXT NOT NULL,
                    content TEXT NOT NULL,
                    timestamp REAL NOT NULL
                );
                CREATE TABLE IF NOT EXISTS user_preferences (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL,
                    updated_at REAL NOT NULL
                );
            """)

    def save_message(self, session_id: str, role: str, content: str) -> None:
        now = time.time()
        if self.is_postgres:
            try:
                conn = self._get_postgres_connection()
                with conn.cursor() as cur:
                    cur.execute(
                        "INSERT INTO conversations (session_id, role, content, timestamp) VALUES (%s, %s, %s, %s)",
                        (session_id, role, content, now)
                    )
                conn.commit()
                conn.close()
                return
            except Exception as e:
                logger.error(f"PostgreSQL save_message error: {e}")

        conn = self._get_sqlite_connection()
        with conn:
            conn.execute(
                "INSERT INTO conversations (session_id, role, content, timestamp) VALUES (?, ?, ?, ?)",
                (session_id, role, content, now)
            )

    def get_history(self, session_id: str, limit: int = 10) -> List[Dict[str, Any]]:
        if self.is_postgres:
            try:
                conn = self._get_postgres_connection()
                with conn.cursor() as cur:
                    cur.execute(
                        "SELECT role, content, timestamp FROM conversations WHERE session_id = %s ORDER BY id DESC LIMIT %s",
                        (session_id, limit)
                    )
                    rows = cur.fetchall()
                conn.close()
                return [{"role": r["role"], "content": r["content"], "timestamp": r["timestamp"]} for r in reversed(rows)]
            except Exception as e:
                logger.error(f"PostgreSQL get_history error: {e}")

        conn = self._get_sqlite_connection()
        cur = conn.cursor()
        cur.execute(
            "SELECT role, content, timestamp FROM conversations WHERE session_id = ? ORDER BY id DESC LIMIT ?",
            (session_id, limit)
        )
        rows = cur.fetchall()
        return [{"role": r["role"], "content": r["content"], "timestamp": r["timestamp"]} for r in reversed(rows)]


persistent_store = PersistentStore()
