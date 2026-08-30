"""
Memory-Augmented Graph (MAG) Structured Fact and Hardware Profile Store.
"""

import json
import logging
import os
import sqlite3
import time
from typing import Any, Optional

logger = logging.getLogger(__name__)

DEFAULT_HARDWARE_PROFILE = {
    "bluetooth_available": True,
    "torch_available": True,
    "camera_available": True,
    "max_volume": 100,
}


class MAGStore:
    def __init__(self, db_path: Optional[str] = None) -> None:
        self.db_path = db_path or os.getenv("JARVIS_DB_PATH", "jarvis_memory.db")
        self._ensure_db_dir()
        self._init_db()

    def _ensure_db_dir(self) -> None:
        db_dir = os.path.dirname(os.path.abspath(self.db_path))
        if db_dir and not os.path.exists(db_dir):
            os.makedirs(db_dir, exist_ok=True)

    def _get_connection(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        return conn

    def _init_db(self) -> None:
        conn = self._get_connection()
        try:
            with conn:
                conn.executescript("""
                    CREATE TABLE IF NOT EXISTS mag_facts (
                        key TEXT PRIMARY KEY,
                        value_json TEXT NOT NULL,
                        category TEXT NOT NULL DEFAULT 'general',
                        updated_at REAL NOT NULL
                    );
                    CREATE TABLE IF NOT EXISTS mag_hardware_profiles (
                        device_id TEXT PRIMARY KEY,
                        profile_json TEXT NOT NULL,
                        updated_at REAL NOT NULL
                    );
                """)
        finally:
            conn.close()

    def set_fact(self, key: str, value: Any, category: str = "general") -> None:
        now = time.time()
        value_json = json.dumps(value)
        conn = self._get_connection()
        try:
            with conn:
                conn.execute(
                    """
                    INSERT INTO mag_facts (key, value_json, category, updated_at)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT(key) DO UPDATE SET
                        value_json = excluded.value_json,
                        category = excluded.category,
                        updated_at = excluded.updated_at
                    """,
                    (key, value_json, category, now),
                )
        finally:
            conn.close()

    def get_fact(self, key: str) -> Optional[Any]:
        conn = self._get_connection()
        try:
            cur = conn.cursor()
            cur.execute("SELECT value_json FROM mag_facts WHERE key = ?", (key,))
            row = cur.fetchone()
            if row is not None:
                return json.loads(row["value_json"])
            return None
        finally:
            conn.close()

    def get_facts_by_category(self, category: str) -> dict[str, Any]:
        conn = self._get_connection()
        try:
            cur = conn.cursor()
            cur.execute(
                "SELECT key, value_json FROM mag_facts WHERE category = ?",
                (category,),
            )
            rows = cur.fetchall()
            return {row["key"]: json.loads(row["value_json"]) for row in rows}
        finally:
            conn.close()

    def set_hardware_profile(self, device_id: str, profile: dict[str, Any]) -> None:
        now = time.time()
        profile_json = json.dumps(profile)
        conn = self._get_connection()
        try:
            with conn:
                conn.execute(
                    """
                    INSERT INTO mag_hardware_profiles (device_id, profile_json, updated_at)
                    VALUES (?, ?, ?)
                    ON CONFLICT(device_id) DO UPDATE SET
                        profile_json = excluded.profile_json,
                        updated_at = excluded.updated_at
                    """,
                    (device_id, profile_json, now),
                )
        finally:
            conn.close()

    def get_hardware_profile(self, device_id: str = "default_device") -> dict[str, Any]:
        conn = self._get_connection()
        try:
            cur = conn.cursor()
            cur.execute(
                "SELECT profile_json FROM mag_hardware_profiles WHERE device_id = ?",
                (device_id,),
            )
            row = cur.fetchone()
            if row is not None:
                return json.loads(row["profile_json"])
            return dict(DEFAULT_HARDWARE_PROFILE)
        finally:
            conn.close()


mag_store = MAGStore()
