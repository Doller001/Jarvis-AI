"""
Tests for Multimodal Sensory Payload Models and Unified Multimodal Memory Coordinator.
"""

from unittest.mock import MagicMock
import pytest

from app.agent.execution_models import MultimodalInputPayload, SensoryTelemetry
from app.memory.cag_cache import CAGCache, cag_cache
from app.memory.mag_store import MAGStore, mag_store
from app.memory.memory_manager import MemoryManager, memory_manager
from app.memory.multimodal_memory import MultimodalMemoryCoordinator, multimodal_memory
from app.memory.persistent_store import PersistentStore, persistent_store
from app.memory.rag_engine import RAGEngine, rag_engine


def test_sensory_telemetry_defaults_and_serialization():
    telemetry = SensoryTelemetry()
    assert telemetry.battery_level is None
    assert telemetry.is_charging is None
    assert telemetry.network_type is None
    assert telemetry.volume_level is None
    assert telemetry.current_audio_output is None
    assert telemetry.extra_sensors == {}

    data = telemetry.model_dump()
    assert data["battery_level"] is None
    assert data["extra_sensors"] == {}

    populated = SensoryTelemetry(
        battery_level=85,
        is_charging=True,
        network_type="wifi",
        volume_level=70,
        current_audio_output="speaker",
        extra_sensors={"ambient_light": "dim", "proximity": False},
    )
    assert populated.battery_level == 85
    assert populated.is_charging is True
    assert populated.network_type == "wifi"
    assert populated.volume_level == 70
    assert populated.current_audio_output == "speaker"
    assert populated.extra_sensors["ambient_light"] == "dim"


def test_multimodal_input_payload_defaults_and_uuid_generation():
    payload1 = MultimodalInputPayload(text="Turn on living room lights")
    payload2 = MultimodalInputPayload(text="Turn off living room lights")

    assert payload1.text == "Turn on living room lights"
    assert payload1.session_id == "default-session"
    assert payload1.request_id.startswith("req-")
    assert payload2.request_id.startswith("req-")
    assert payload1.request_id != payload2.request_id

    sensory = SensoryTelemetry(battery_level=90, network_type="cellular")
    payload3 = MultimodalInputPayload(
        text="Analyze this image",
        session_id="custom-session",
        request_id="custom-req-123",
        sensory_data=sensory,
        image_base64="data:image/png;base64,iVBORw0KGgo...",
        image_uri="https://example.com/photo.jpg",
    )
    assert payload3.session_id == "custom-session"
    assert payload3.request_id == "custom-req-123"
    assert payload3.sensory_data is not None
    assert payload3.sensory_data.battery_level == 90
    assert payload3.image_base64.startswith("data:image/png")
    assert payload3.image_uri == "https://example.com/photo.jpg"


def test_multimodal_memory_coordinator_singleton():
    assert isinstance(multimodal_memory, MultimodalMemoryCoordinator)
    assert isinstance(multimodal_memory.cag, CAGCache)
    assert isinstance(multimodal_memory.rag, RAGEngine)
    assert isinstance(multimodal_memory.mag, MAGStore)
    assert isinstance(multimodal_memory.persistent, PersistentStore)


def test_multimodal_memory_coordinator_retrieve_context_with_mocks():
    mock_cag = MagicMock(spec=CAGCache)
    mock_rag = MagicMock(spec=RAGEngine)
    mock_mag = MagicMock(spec=MAGStore)
    mock_persistent = MagicMock(spec=PersistentStore)

    mock_mag.get_facts_by_category.side_effect = lambda cat: {
        "profile": {"user_name": "Tony", "preferred_tone": "butler"},
        "general": {"timezone": "UTC+5:30", "preferred_music_app": "spotify"},
    }.get(cat, {})

    mock_rag.search.return_value = [
        {"chunk_id": "chk-1", "content": "Meeting scheduled at 10 AM", "score": 1.0},
        {"chunk_id": "chk-2", "content": "Dark mode preference confirmed", "score": 0.8},
    ]

    coordinator = MultimodalMemoryCoordinator(
        cag=mock_cag,
        rag=mock_rag,
        mag=mock_mag,
        persistent=mock_persistent,
    )

    sensory = SensoryTelemetry(battery_level=42, is_charging=False, network_type="wifi")
    context = coordinator.retrieve_context(
        query="what is my schedule",
        session_id="session-xyz",
        sensory=sensory,
    )

    # Check MAG facts aggregation
    assert context["facts"] == {
        "user_name": "Tony",
        "preferred_tone": "butler",
        "timezone": "UTC+5:30",
        "preferred_music_app": "spotify",
    }
    mock_mag.get_facts_by_category.assert_any_call("profile")
    mock_mag.get_facts_by_category.assert_any_call("general")

    # Check RAG results
    assert context["relevant_rag"] == [
        "Meeting scheduled at 10 AM",
        "Dark mode preference confirmed",
    ]
    mock_rag.search.assert_called_once_with("what is my schedule", session_id="session-xyz")

    # Check Sensory data
    assert context["sensory"]["battery_level"] == 42
    assert context["sensory"]["network_type"] == "wifi"


def test_multimodal_memory_coordinator_retrieve_context_no_sensory():
    mock_rag = MagicMock(spec=RAGEngine)
    mock_mag = MagicMock(spec=MAGStore)
    mock_mag.get_facts_by_category.return_value = {}
    mock_rag.search.return_value = []

    coordinator = MultimodalMemoryCoordinator(rag=mock_rag, mag=mock_mag)
    context = coordinator.retrieve_context(query="hello", session_id="default-session", sensory=None)

    assert context["facts"] == {}
    assert context["relevant_rag"] == []
    assert context["sensory"] == {}


def test_multimodal_memory_coordinator_record_interaction():
    mock_rag = MagicMock(spec=RAGEngine)
    mock_persistent = MagicMock(spec=PersistentStore)

    coordinator = MultimodalMemoryCoordinator(rag=mock_rag, persistent=mock_persistent)
    coordinator.record_interaction(
        session_id="sess-100",
        role="user",
        content="Play jazz playlist",
        metadata={"source": "voice"},
    )

    mock_rag.index_chunk.assert_called_once_with(
        session_id="sess-100",
        content="Play jazz playlist",
        role="user",
        metadata={"source": "voice"},
    )
    mock_persistent.save_message.assert_called_once_with(
        session_id="sess-100",
        role="user",
        content="Play jazz playlist",
    )


def test_memory_manager_dual_logging():
    mock_persistent = MagicMock(spec=PersistentStore)
    mock_rag = MagicMock(spec=RAGEngine)

    manager = MemoryManager(persistent=mock_persistent, rag=mock_rag)

    # User message
    manager.record_user_message("session-abc", "Turn up the volume")
    mock_persistent.save_message.assert_called_once_with("session-abc", "user", "Turn up the volume")
    mock_rag.index_chunk.assert_called_once_with("session-abc", "Turn up the volume", role="user")

    # Assistant message
    manager.record_assistant_message("session-abc", "Volume set to 80 percent")
    mock_persistent.save_message.assert_called_with("session-abc", "assistant", "Volume set to 80 percent")
    mock_rag.index_chunk.assert_called_with("session-abc", "Volume set to 80 percent", role="assistant")

    # Empty messages are ignored
    mock_persistent.reset_mock()
    mock_rag.reset_mock()
    manager.record_user_message("session-abc", "")
    manager.record_assistant_message("session-abc", "")
    mock_persistent.save_message.assert_not_called()
    mock_rag.index_chunk.assert_not_called()


def test_unified_multimodal_memory_integration(tmp_path):
    db_file = str(tmp_path / "integration_multimodal.db")

    cag = CAGCache(max_entries=10)
    rag = RAGEngine(db_path=db_file)
    mag = MAGStore(db_path=db_file)
    persistent = PersistentStore()
    persistent.db_path = db_file
    persistent.is_supabase = False
    persistent.is_mongodb = False
    persistent.is_postgres = False
    persistent._init_db()

    # 1. Populate MAG facts
    mag.set_fact("user_name", "Bruce Wayne", category="profile")
    mag.set_fact("city", "Gotham", category="general")

    # 2. Coordinator
    coordinator = MultimodalMemoryCoordinator(
        cag=cag,
        rag=rag,
        mag=mag,
        persistent=persistent,
    )

    # 3. Record interaction
    coordinator.record_interaction(
        session_id="bat-cave-1",
        role="assistant",
        content="Batmobile security system armed and online.",
        metadata={"priority": "high"},
    )

    # 4. Retrieve context
    sensory = SensoryTelemetry(battery_level=100, is_charging=True, network_type="cellular")
    ctx = coordinator.retrieve_context(
        query="Batmobile security",
        session_id="bat-cave-1",
        sensory=sensory,
    )

    assert ctx["facts"] == {"user_name": "Bruce Wayne", "city": "Gotham"}
    assert len(ctx["relevant_rag"]) >= 1
    assert "Batmobile security system" in ctx["relevant_rag"][0]
    assert ctx["sensory"]["battery_level"] == 100
    assert ctx["sensory"]["is_charging"] is True
