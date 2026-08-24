"""Retrieval subsystem for Jarvis (semantic search over local vector DBs)."""

from app.retrieval.music_index import MusicIndex, music_index

__all__ = ["MusicIndex", "music_index"]
