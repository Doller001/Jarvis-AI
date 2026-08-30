"""
Unified Multimodal Memory Coordinator connecting CAG, RAG, and MAG tiers.
"""

import logging
from typing import Any, Optional

from app.agent.execution_models import SensoryTelemetry
from app.memory.cag_cache import CAGCache, cag_cache
from app.memory.mag_store import MAGStore, mag_store
from app.memory.persistent_store import PersistentStore, persistent_store
from app.memory.rag_engine import RAGEngine, rag_engine

logger = logging.getLogger(__name__)


class MultimodalMemoryCoordinator:
    """
    Coordinator orchestrating Context-Aware Generator (CAG) fast cache,
    Retrieval-Augmented Generation (RAG) episodic search, and
    Memory-Augmented Graph (MAG) structured facts and hardware profiles.
    """

    def __init__(
        self,
        cag: Optional[CAGCache] = None,
        rag: Optional[RAGEngine] = None,
        mag: Optional[MAGStore] = None,
        persistent: Optional[PersistentStore] = None,
    ) -> None:
        self.cag = cag or cag_cache
        self.rag = rag or rag_engine
        self.mag = mag or mag_store
        self.persistent = persistent or persistent_store

    def retrieve_context(
        self,
        query: str,
        session_id: str = "default-session",
        sensory: Optional[SensoryTelemetry] = None,
    ) -> dict[str, Any]:
        """
        Synthesizes structured facts (MAG), semantic search results (RAG),
        and current sensory telemetry into a unified context payload.
        """
        mag_profile_facts = self.mag.get_facts_by_category("profile") or {}
        mag_general_facts = self.mag.get_facts_by_category("general") or {}
        facts = {**mag_profile_facts, **mag_general_facts}

        rag_results = self.rag.search(query, session_id=session_id)
        relevant_rag = [
            r["content"]
            for r in rag_results
            if isinstance(r, dict) and "content" in r
        ]

        if sensory is None:
            sensory_dict: dict[str, Any] = {}
        elif isinstance(sensory, dict):
            sensory_dict = sensory
        elif hasattr(sensory, "model_dump"):
            sensory_dict = sensory.model_dump()
        elif hasattr(sensory, "dict"):
            sensory_dict = sensory.dict()
        else:
            sensory_dict = dict(sensory)

        return {
            "facts": facts,
            "relevant_rag": relevant_rag,
            "sensory": sensory_dict,
        }

    def record_interaction(
        self,
        session_id: str,
        role: str,
        content: str,
        metadata: Optional[dict[str, Any]] = None,
    ) -> None:
        """
        Indexes interaction into RAG engine for episodic semantic search
        and records it into the persistent conversation history store.
        """
        if not content or not content.strip():
            return

        self.rag.index_chunk(
            session_id=session_id,
            content=content,
            role=role,
            metadata=metadata,
        )
        self.persistent.save_message(
            session_id=session_id,
            role=role,
            content=content,
        )


multimodal_memory = MultimodalMemoryCoordinator()
