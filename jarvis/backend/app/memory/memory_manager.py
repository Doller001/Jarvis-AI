"""
MemoryManager facade for Jarvis.
"""

from typing import List, Dict, Any
from app.memory.persistent_store import persistent_store


class MemoryManager:
    def record_user_message(self, session_id: str, text: str) -> None:
        if text:
            persistent_store.save_message(session_id, "user", text)

    def record_assistant_message(self, session_id: str, text: str) -> None:
        if text:
            persistent_store.save_message(session_id, "assistant", text)

    def get_conversation_history(self, session_id: str, limit: int = 10) -> List[Dict[str, Any]]:
        return persistent_store.get_history(session_id, limit=limit)


memory_manager = MemoryManager()
