import hashlib
import json
import logging
import os
import secrets
import time
from dataclasses import dataclass, field
from typing import Any

from app.db.supabase_client import supabase_client

logger = logging.getLogger("jarvis.security.device")

DB_PATH = os.getenv("JARVIS_DB_PATH", "jarvis_memory.db")


@dataclass
class DeviceIdentity:
    device_id: str
    device_name: str
    device_model: str
    os_version: str
    first_seen: float
    last_seen: float
    trusted: bool = False
    trust_token: str | None = None
    metadata: dict = field(default_factory=dict)


@dataclass
class AuthSession:
    session_id: str
    device_id: str
    refresh_token_hash: str
    created_at: float
    expires_at: float
    last_used_at: float
    revoked: bool = False


class DeviceRegistry:
    """Device registry with PostgreSQL persistence and in-memory caching."""

    def __init__(self) -> None:
        self._devices: dict[str, DeviceIdentity] = {}
        self._trust_tokens: dict[str, str] = {}  # trust_token -> device_id
        self._sessions: dict[str, AuthSession] = {}  # session_id -> AuthSession
        self._init_db_schema()
        self._load()

    def _init_db_schema(self) -> None:
        """Initializes PostgreSQL tables if database connection is available."""
        if supabase_client.is_postgres_enabled:
            try:
                import psycopg2
                url = supabase_client.db_url
                if url.startswith("postgres://"):
                    url = url.replace("postgres://", "postgresql://", 1)
                conn = psycopg2.connect(url, connect_timeout=5)
                with conn.cursor() as cur:
                    cur.execute("""
                        CREATE TABLE IF NOT EXISTS devices (
                            device_id VARCHAR(64) PRIMARY KEY,
                            device_name VARCHAR(128) NOT NULL,
                            device_model VARCHAR(128) NOT NULL,
                            os_version VARCHAR(64) NOT NULL,
                            trusted BOOLEAN DEFAULT FALSE,
                            first_seen DOUBLE PRECISION NOT NULL,
                            last_seen DOUBLE PRECISION NOT NULL,
                            trust_token VARCHAR(128),
                            metadata JSONB DEFAULT '{}'::jsonb
                        );
                        CREATE TABLE IF NOT EXISTS auth_sessions (
                            session_id VARCHAR(64) PRIMARY KEY,
                            device_id VARCHAR(64) REFERENCES devices(device_id) ON DELETE CASCADE,
                            refresh_token_hash VARCHAR(64) NOT NULL,
                            created_at DOUBLE PRECISION NOT NULL,
                            expires_at DOUBLE PRECISION NOT NULL,
                            last_used_at DOUBLE PRECISION NOT NULL,
                            revoked BOOLEAN DEFAULT FALSE
                        );
                        CREATE INDEX IF NOT EXISTS idx_auth_sessions_hash ON auth_sessions(refresh_token_hash);
                    """)
                conn.commit()
                conn.close()
                logger.info("PostgreSQL device registry schema initialized.")
            except Exception as e:
                logger.warning(f"PostgreSQL schema init deferred: {e}")

    def _load(self) -> None:
        """Load devices from PostgreSQL or local JSON file."""
        if supabase_client.is_postgres_enabled:
            try:
                import psycopg2
                import psycopg2.extras
                url = supabase_client.db_url
                if url.startswith("postgres://"):
                    url = url.replace("postgres://", "postgresql://", 1)
                conn = psycopg2.connect(url, cursor_factory=psycopg2.extras.RealDictCursor, connect_timeout=5)
                with conn.cursor() as cur:
                    cur.execute("SELECT * FROM devices;")
                    rows = cur.fetchall()
                    for r in rows:
                        identity = DeviceIdentity(
                            device_id=r["device_id"],
                            device_name=r["device_name"],
                            device_model=r["device_model"],
                            os_version=r["os_version"],
                            trusted=r.get("trusted", False),
                            first_seen=r["first_seen"],
                            last_seen=r["last_seen"],
                            trust_token=r.get("trust_token"),
                            metadata=r.get("metadata") or {}
                        )
                        self._devices[identity.device_id] = identity
                        if identity.trust_token:
                            self._trust_tokens[identity.trust_token] = identity.device_id

                    cur.execute("SELECT * FROM auth_sessions WHERE revoked = FALSE;")
                    s_rows = cur.fetchall()
                    for s in s_rows:
                        session = AuthSession(
                            session_id=s["session_id"],
                            device_id=s["device_id"],
                            refresh_token_hash=s["refresh_token_hash"],
                            created_at=s["created_at"],
                            expires_at=s["expires_at"],
                            last_used_at=s["last_used_at"],
                            revoked=s.get("revoked", False)
                        )
                        self._sessions[session.session_id] = session

                conn.close()
                logger.info(f"Loaded {len(self._devices)} devices from PostgreSQL")
                return
            except Exception as e:
                logger.warning(f"PostgreSQL load failed, falling back to local storage: {e}")

        # Local fallback
        registry_path = os.path.join(os.path.dirname(DB_PATH), "device_registry.json")
        if os.path.exists(registry_path):
            try:
                with open(registry_path, "r") as f:
                    data = json.load(f)
                for d in data:
                    identity = DeviceIdentity(**d)
                    self._devices[identity.device_id] = identity
                    if identity.trust_token:
                        self._trust_tokens[identity.trust_token] = identity.device_id
                logger.info(f"Loaded {len(self._devices)} registered devices from local cache")
            except Exception as e:
                logger.error(f"Failed to load device registry: {e}")

    def _persist_device(self, identity: DeviceIdentity) -> None:
        """Persists device to PostgreSQL and local cache."""
        if supabase_client.is_postgres_enabled:
            try:
                import psycopg2
                url = supabase_client.db_url
                if url.startswith("postgres://"):
                    url = url.replace("postgres://", "postgresql://", 1)
                conn = psycopg2.connect(url, connect_timeout=5)
                with conn.cursor() as cur:
                    cur.execute("""
                        INSERT INTO devices (device_id, device_name, device_model, os_version, trusted, first_seen, last_seen, trust_token, metadata)
                        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
                        ON CONFLICT (device_id) DO UPDATE SET
                            device_name = EXCLUDED.device_name,
                            device_model = EXCLUDED.device_model,
                            os_version = EXCLUDED.os_version,
                            trusted = EXCLUDED.trusted,
                            last_seen = EXCLUDED.last_seen,
                            trust_token = EXCLUDED.trust_token;
                    """, (
                        identity.device_id, identity.device_name, identity.device_model,
                        identity.os_version, identity.trusted, identity.first_seen,
                        identity.last_seen, identity.trust_token, json.dumps(identity.metadata)
                    ))
                conn.commit()
                conn.close()
                return
            except Exception as e:
                logger.warning(f"PostgreSQL persist failed, using local: {e}")

        # Local fallback persistence
        try:
            registry_path = os.path.join(os.path.dirname(DB_PATH), "device_registry.json")
            os.makedirs(os.path.dirname(registry_path), exist_ok=True)
            data = [d.__dict__ for d in self._devices.values()]
            with open(registry_path, "w") as f:
                json.dump(data, f, indent=2)
        except Exception as e:
            logger.error(f"Failed to save device registry: {e}")

    def register_device(
        self,
        device_name: str,
        device_model: str,
        os_version: str,
        device_id: str | None = None,
    ) -> DeviceIdentity:
        now = time.time()
        if device_id and device_id in self._devices:
            existing = self._devices[device_id]
            existing.last_seen = now
            existing.device_name = device_name
            existing.device_model = device_model
            existing.os_version = os_version
            self._persist_device(existing)
            logger.info(f"Updated existing device: {device_id}")
            return existing

        if not device_id:
            device_id = self._generate_device_id(device_name, device_model)

        if device_id in self._devices:
            self._devices[device_id].last_seen = now
            self._persist_device(self._devices[device_id])
            return self._devices[device_id]

        trust_token = secrets.token_urlsafe(32)
        identity = DeviceIdentity(
            device_id=device_id,
            device_name=device_name,
            device_model=device_model,
            os_version=os_version,
            first_seen=now,
            last_seen=now,
            trusted=False,
            trust_token=trust_token,
        )
        self._devices[device_id] = identity
        self._trust_tokens[trust_token] = device_id
        self._persist_device(identity)
        logger.info(f"Registered new device: {device_id} ({device_name})")
        return identity

    def get_device(self, device_id: str) -> DeviceIdentity | None:
        device = self._devices.get(device_id)
        if device:
            device.last_seen = time.time()
        return device

    def trust_device(self, device_id: str) -> bool:
        device = self._devices.get(device_id)
        if device:
            device.trusted = True
            device.trust_token = None
            self._persist_device(device)
            logger.info(f"Device trusted: {device_id}")
            return True
        return False

    def is_device_registered(self, device_id: str) -> bool:
        return device_id in self._devices

    def list_devices(self) -> list[DeviceIdentity]:
        return list(self._devices.values())

    def touch_device(self, device_id: str) -> None:
        device = self._devices.get(device_id)
        if device:
            device.last_seen = time.time()

    def revoke_device(self, device_id: str) -> bool:
        device = self._devices.pop(device_id, None)
        if device and device.trust_token:
            self._trust_tokens.pop(device.trust_token, None)
        return device is not None

    def create_session(
        self,
        session_id: str,
        device_id: str,
        refresh_token: str,
        expires_at: float
    ) -> AuthSession:
        """Creates and stores a hashed refresh token session."""
        now = time.time()
        h = hashlib.sha256(refresh_token.encode()).hexdigest()
        session = AuthSession(
            session_id=session_id,
            device_id=device_id,
            refresh_token_hash=h,
            created_at=now,
            expires_at=expires_at,
            last_used_at=now,
            revoked=False
        )
        self._sessions[session_id] = session

        if supabase_client.is_postgres_enabled:
            try:
                import psycopg2
                url = supabase_client.db_url
                if url.startswith("postgres://"):
                    url = url.replace("postgres://", "postgresql://", 1)
                conn = psycopg2.connect(url, connect_timeout=5)
                with conn.cursor() as cur:
                    cur.execute("""
                        INSERT INTO auth_sessions (session_id, device_id, refresh_token_hash, created_at, expires_at, last_used_at, revoked)
                        VALUES (%s, %s, %s, %s, %s, %s, %s)
                        ON CONFLICT (session_id) DO UPDATE SET
                            refresh_token_hash = EXCLUDED.refresh_token_hash,
                            last_used_at = EXCLUDED.last_used_at,
                            revoked = EXCLUDED.revoked;
                    """, (
                        session.session_id, session.device_id, session.refresh_token_hash,
                        session.created_at, session.expires_at, session.last_used_at, session.revoked
                    ))
                conn.commit()
                conn.close()
            except Exception as e:
                logger.warning(f"PostgreSQL session insert failed: {e}")

        return session

    def validate_and_rotate_session(
        self,
        old_session_id: str,
        new_session_id: str,
        device_id: str,
        old_refresh_token: str,
        new_refresh_token: str,
        expires_at: float
    ) -> bool:
        """Validates current refresh token hash, rotates session, and detects token reuse."""
        old_hash = hashlib.sha256(old_refresh_token.encode()).hexdigest()
        session = self._sessions.get(old_session_id)

        if session is None or session.revoked or session.refresh_token_hash != old_hash:
            logger.warning(f"Invalid or reused refresh token for session {old_session_id} - revoking device sessions")
            self.revoke_device_sessions(device_id)
            return False

        if session.expires_at < time.time():
            logger.warning(f"Refresh token session {old_session_id} has expired")
            session.revoked = True
            return False

        # Invalidate old session and create new rotated session
        session.revoked = True
        self.create_session(new_session_id, device_id, new_refresh_token, expires_at)
        return True

    def revoke_device_sessions(self, device_id: str) -> None:
        """Revokes all active sessions for a device upon token reuse or compromise."""
        for s in self._sessions.values():
            if s.device_id == device_id:
                s.revoked = True

    def _generate_device_id(self, name: str, model: str) -> str:
        raw = f"{name}:{model}:{secrets.token_hex(8)}"
        return hashlib.sha256(raw.encode()).hexdigest()[:24]


device_registry = DeviceRegistry()

