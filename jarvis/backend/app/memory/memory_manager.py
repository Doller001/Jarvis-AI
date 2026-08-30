"""
MemoryManager facade for Jarvis.
"""

from typing import Any, Optional

from app.memory.persistent_store import PersistentStore, persistent_store
from app.memory.rag_engine import RAGEngine, rag_engine


class MemoryManager:
    def __init__(
        self,
        persistent: Optional[PersistentStore] = None,
        rag: Optional[RAGEngine] = None,
    ) -> None:
        self.persistent = persistent or persistent_store
        self.rag = rag or rag_engine

    def record_user_message(self, session_id: str, text: str) -> None:
        if text:
            self.persistent.save_message(session_id, "user", text)
            self.rag.index_chunk(session_id, text, role="user")

    def record_assistant_message(self, session_id: str, text: str) -> None:
        if text:
            self.persistent.save_message(session_id, "assistant", text)
            self.rag.index_chunk(session_id, text, role="assistant")

    def get_conversation_history(self, session_id: str, limit: int = 10) -> list[dict[str, Any]]:
        return self.persistent.get_history(session_id, limit=limit)


memory_manager = MemoryManager()

