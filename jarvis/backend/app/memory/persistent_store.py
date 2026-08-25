"""
Persistent Store for Jarvis supporting Supabase (REST & PostgreSQL), MongoDB Atlas, and SQLite.
"""

import logging
import os
import time
from typing import Any, Optional

from app.db.supabase_client import SupabaseClient, supabase_client

logger = logging.getLogger(__name__)


class PersistentStore:
    def __init__(self, supabase: Optional[SupabaseClient] = None) -> None:
        self.supabase = supabase or supabase_client
        self.db_url = os.getenv("DATABASE_URL") or os.getenv("SUPABASE_DATABASE_URL")
        self.db_path = os.getenv("JARVIS_DB_PATH", "jarvis_memory.db")
        self.mongodb_uri = os.getenv("MONGODB_URI") or os.getenv("MONGO_URI") or os.getenv("MONGODB_URL")

        self.is_supabase = bool(self.supabase and self.supabase.is_active)
        self.is_mongodb = False
        self.is_postgres = False
        self.mongo_client = None
        self.mongo_db = None

        if self.is_supabase:
            logger.info("Initializing Jarvis Persistent Store with Supabase DB connectivity.")

        if not self.is_supabase and self.mongodb_uri and (
            self.mongodb_uri.startswith("mongodb://") or self.mongodb_uri.startswith("mongodb+srv://")
        ):
            try:
                import pymongo
                self.mongo_client = pymongo.MongoClient(self.mongodb_uri, serverSelectionTimeoutMS=5000)
                self.mongo_client.admin.command('ping')
                
                db_name = "jarvis"
                try:
                    parsed_db = self.mongo_client.get_default_database()
                    if parsed_db is not None:
                        db_name = parsed_db.name
                except Exception:
                    pass
                    
                self.mongo_db = self.mongo_client[db_name]
                self.is_mongodb = True
                logger.info(f"Initializing Jarvis Persistent Store with MongoDB Atlas connection (db: '{db_name}').")
            except Exception as e:
                logger.warning(f"MongoDB Atlas connection failed ({e}); checking SQLite fallback.")
                self.is_mongodb = False

        if not self.is_supabase and not self.is_mongodb and self.db_url and (
            self.db_url.startswith("postgresql://") or self.db_url.startswith("postgres://")
        ):
            self.is_postgres = True
            logger.info("Initializing Jarvis Persistent Store with PostgreSQL connection.")
        elif not self.is_supabase and not self.is_mongodb:
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
        if self.is_supabase:
            # Supabase schema is verified via client or migrations
            return

        if self.is_mongodb and self.mongo_db is not None:
            try:
                import pymongo
                self.mongo_db.conversations.create_index([("session_id", pymongo.ASCENDING), ("timestamp", pymongo.DESCENDING)])
                self.mongo_db.user_preferences.create_index("key", unique=True)
                return
            except Exception as e:
                logger.error(f"Error creating MongoDB Atlas indices: {e}")

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
                logger.error(f"Error initializing PostgreSQL tables: {e}; falling back to SQLite.")
                self.is_postgres = False

        # SQLite fallback
        conn = self._get_sqlite_connection()
        try:
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
        finally:
            conn.close()

    def save_message(self, session_id: str, role: str, content: str) -> None:
        now = time.time()
        
        # 1. Supabase Priority
        if self.is_supabase:
            ok = self.supabase.save_conversation_message(session_id, role, content)
            if ok:
                return

        # 2. MongoDB
        if self.is_mongodb and self.mongo_db is not None:
            try:
                self.mongo_db.conversations.insert_one({
                    "session_id": session_id,
                    "role": role,
                    "content": content,
                    "timestamp": now
                })
                return
            except Exception as e:
                logger.error(f"MongoDB save_message error: {e}")

        # 3. PostgreSQL Direct
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

        # 4. SQLite Fallback
        conn = self._get_sqlite_connection()
        try:
            with conn:
                conn.execute(
                    "INSERT INTO conversations (session_id, role, content, timestamp) VALUES (?, ?, ?, ?)",
                    (session_id, role, content, now)
                )
        finally:
            conn.close()

    def get_history(self, session_id: str, limit: int = 10) -> list[dict[str, Any]]:
        # 1. Supabase Priority
        if self.is_supabase:
            history = self.supabase.get_conversation_history(session_id, limit=limit)
            if history is not None:
                return history

        # 2. MongoDB
        if self.is_mongodb and self.mongo_db is not None:
            try:
                import pymongo
                cursor = self.mongo_db.conversations.find(
                    {"session_id": session_id},
                    {"_id": 0, "role": 1, "content": 1, "timestamp": 1}
                ).sort("timestamp", pymongo.DESCENDING).limit(limit)
                rows = list(cursor)
                return [{"role": r["role"], "content": r["content"], "timestamp": r["timestamp"]} for r in reversed(rows)]
            except Exception as e:
                logger.error(f"MongoDB get_history error: {e}")

        # 3. PostgreSQL Direct
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

        # 4. SQLite Fallback
        conn = self._get_sqlite_connection()
        try:
            cur = conn.cursor()
            cur.execute(
                "SELECT role, content, timestamp FROM conversations WHERE session_id = ? ORDER BY id DESC LIMIT ?",
                (session_id, limit)
            )
            rows = cur.fetchall()
        finally:
            conn.close()
        return [{"role": r["role"], "content": r["content"], "timestamp": r["timestamp"]} for r in reversed(rows)]


persistent_store = PersistentStore()
