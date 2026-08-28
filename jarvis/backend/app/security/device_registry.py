"""
Device Registry for Jarvis AI Backend.
Manages per-device identity, registration, and trust status.
"""

import hashlib
import logging
import os
import secrets
import time
from dataclasses import dataclass, field

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


class DeviceRegistry:
    """In-memory device registry with file-backed persistence."""

    def __init__(self) -> None:
        self._devices: dict[str, DeviceIdentity] = {}
        self._trust_tokens: dict[str, str] = {}  # trust_token -> device_id
        self._load()

    def _load(self) -> None:
        """Load devices from JSON file."""
        import json
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
                logger.info(f"Loaded {len(self._devices)} registered devices")
            except Exception as e:
                logger.error(f"Failed to load device registry: {e}")

    def _save(self) -> None:
        """Persist devices to JSON file."""
        import json
        registry_path = os.path.join(os.path.dirname(DB_PATH), "device_registry.json")
        try:
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
        """Register a new device or update existing."""
        now = time.time()

        if device_id and device_id in self._devices:
            existing = self._devices[device_id]
            existing.last_seen = now
            existing.device_name = device_name
            existing.device_model = device_model
            existing.os_version = os_version
            self._save()
            logger.info(f"Updated existing device: {device_id}")
            return existing

        if not device_id:
            device_id = self._generate_device_id(device_name, device_model)

        if device_id in self._devices:
            self._devices[device_id].last_seen = now
            self._save()
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
        self._save()
        logger.info(f"Registered new device: {device_id} ({device_name})")
        return identity

    def get_device(self, device_id: str) -> DeviceIdentity | None:
        device = self._devices.get(device_id)
        if device:
            device.last_seen = time.time()
        return device

    def trust_device(self, device_id: str) -> bool:
        """Mark a device as trusted after initial registration."""
        device = self._devices.get(device_id)
        if device:
            device.trusted = True
            device.trust_token = None  # Invalidate one-time trust token
            self._save()
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
        self._save()
        return device is not None

    def _generate_device_id(self, name: str, model: str) -> str:
        raw = f"{name}:{model}:{secrets.token_hex(8)}"
        return hashlib.sha256(raw.encode()).hexdigest()[:24]


device_registry = DeviceRegistry()
