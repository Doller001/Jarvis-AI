"""
Tests for Memory-Augmented Graph (MAG) Structured Fact and Hardware Profile Store.
"""

import os
import pytest
from app.memory.mag_store import MAGStore, mag_store


@pytest.fixture
def temp_mag_store(tmp_path):
    db_file = str(tmp_path / "test_mag.db")
    return MAGStore(db_path=db_file)


def test_singleton_instance():
    assert isinstance(mag_store, MAGStore)


def test_set_and_get_fact_basic(temp_mag_store):
    store = temp_mag_store
    assert store.get_fact("non_existent_key") is None

    # String value
    store.set_fact("user_name", "Jarvis User", category="profile")
    assert store.get_fact("user_name") == "Jarvis User"

    # Number value
    store.set_fact("user_age", 30, category="profile")
    assert store.get_fact("user_age") == 30

    # Float value
    store.set_fact("battery_level", 98.5, category="device")
    assert store.get_fact("battery_level") == 98.5

    # Boolean value
    store.set_fact("dark_mode", True, category="preferences")
    assert store.get_fact("dark_mode") is True

    # List value
    store.set_fact("favorite_topics", ["ai", "astronomy", "music"], category="preferences")
    assert store.get_fact("favorite_topics") == ["ai", "astronomy", "music"]

    # Dict value
    nested_profile = {
        "theme": "dark",
        "notifications": {"email": False, "push": True},
        "tags": ["admin", "beta_tester"]
    }
    store.set_fact("nested_pref", nested_profile, category="preferences")
    assert store.get_fact("nested_pref") == nested_profile


def test_fact_upsert_overwrite(temp_mag_store):
    store = temp_mag_store
    store.set_fact("status", "initial", category="general")
    assert store.get_fact("status") == "initial"

    # Overwrite fact with new value and new category
    store.set_fact("status", "updated", category="custom")
    assert store.get_fact("status") == "updated"

    # Check category grouping reflects updated category
    general_facts = store.get_facts_by_category("general")
    assert "status" not in general_facts

    custom_facts = store.get_facts_by_category("custom")
    assert custom_facts.get("status") == "updated"


def test_get_facts_by_category(temp_mag_store):
    store = temp_mag_store
    store.set_fact("first_name", "Tony", category="profile")
    store.set_fact("last_name", "Stark", category="profile")
    store.set_fact("ai_model", "gemini", category="settings")
    store.set_fact("notes", "Avenger", category="general")

    profile_facts = store.get_facts_by_category("profile")
    assert profile_facts == {
        "first_name": "Tony",
        "last_name": "Stark"
    }

    settings_facts = store.get_facts_by_category("settings")
    assert settings_facts == {
        "ai_model": "gemini"
    }

    empty_facts = store.get_facts_by_category("non_existent_category")
    assert empty_facts == {}


def test_default_hardware_profile_safe_fallback(temp_mag_store):
    store = temp_mag_store
    expected_default = {
        "bluetooth_available": True,
        "torch_available": True,
        "camera_available": True,
        "max_volume": 100
    }

    # Default device ID
    default_profile = store.get_hardware_profile("default_device")
    assert default_profile == expected_default

    # Unknown device ID fallback
    unknown_profile = store.get_hardware_profile("unknown_phone_12345")
    assert unknown_profile == expected_default


def test_set_and_get_hardware_profile(temp_mag_store):
    store = temp_mag_store
    device_id = "phone_android_9"
    custom_profile = {
        "bluetooth_available": False,
        "torch_available": True,
        "camera_available": True,
        "max_volume": 80,
        "screen_resolution": "1080x2400",
        "has_nfc": True
    }

    store.set_hardware_profile(device_id, custom_profile)
    retrieved = store.get_hardware_profile(device_id)
    assert retrieved == custom_profile

    # Overwrite profile
    updated_profile = {
        "bluetooth_available": True,
        "torch_available": False,
        "camera_available": True,
        "max_volume": 90
    }
    store.set_hardware_profile(device_id, updated_profile)
    assert store.get_hardware_profile(device_id) == updated_profile


def test_custom_db_path(tmp_path):
    custom_db = str(tmp_path / "custom_dir" / "my_mag.db")
    store = MAGStore(db_path=custom_db)
    store.set_fact("test_key", "test_value")
    assert store.get_fact("test_key") == "test_value"
    assert os.path.exists(custom_db)


def test_env_var_db_path(tmp_path, monkeypatch):
    env_db = str(tmp_path / "env_mag.db")
    monkeypatch.setenv("JARVIS_DB_PATH", env_db)
    store = MAGStore()
    store.set_fact("env_key", 12345)
    assert store.get_fact("env_key") == 12345
    assert os.path.exists(env_db)
