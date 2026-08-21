"""Retrieval subsystem for Jarvis (semantic search over local vector DBs)."""

from app.retrieval.music_index import music_index, MusicIndex

__all__ = ["music_index", "MusicIndex"]
