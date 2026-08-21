"""
Music semantic retrieval for Jarvis.

Wraps the ChromaDB vector database built from the youtube_music_ai_dataset
(see build_vector_db.py in that dataset folder).

Design notes:
- Lazy loaded: the embedding model (~90MB) and Chroma client are only
  constructed on first real query, so backend startup stays fast.
- Degrades gracefully: if chromadb/sentence-transformers are not installed or
  the DB directory is missing, `available` is False and search() returns a
  structured error instead of raising. The rest of Jarvis keeps working.
- Metadata filtering is supported (language / era / mood / year range) because
  pure vector similarity is weak at numeric constraints like "songs before 2010".
"""

from __future__ import annotations

import logging
import os
import threading
from typing import Any, Dict, List, Optional

logger = logging.getLogger(__name__)

DEFAULT_DB_PATH = os.path.expanduser(
    "~/Desktop/youtube_music_ai_dataset/vector_db"
)
DB_PATH = os.environ.get("JARVIS_MUSIC_DB_PATH", DEFAULT_DB_PATH)
EMBED_MODEL = os.environ.get("JARVIS_MUSIC_EMBED_MODEL", "all-MiniLM-L6-v2")

SONGS_COLLECTION = "songs"
QUERIES_COLLECTION = "training_queries"


class MusicIndex:
    """Lazy-loading semantic search over the music vector DB."""

    def __init__(self, db_path: str = DB_PATH, model_name: str = EMBED_MODEL) -> None:
        self.db_path = db_path
        self.model_name = model_name
        self._lock = threading.Lock()
        self._loaded = False
        self._load_error: Optional[str] = None
        self._client = None
        self._ef = None
        self._songs = None

    # ---------------- lifecycle ----------------

    @property
    def db_exists(self) -> bool:
        return os.path.isdir(self.db_path)

    def _load(self) -> bool:
        """Build the client/model once. Returns True if usable."""
        if self._loaded:
            return self._load_error is None
        with self._lock:
            if self._loaded:
                return self._load_error is None
            self._loaded = True
            if not self.db_exists:
                self._load_error = f"music vector DB not found at {self.db_path}"
                logger.warning("MusicIndex disabled: %s", self._load_error)
                return False
            try:
                import chromadb
                from chromadb.utils import embedding_functions
            except ImportError as e:
                self._load_error = (
                    f"chromadb/sentence-transformers not installed ({e})"
                )
                logger.warning("MusicIndex disabled: %s", self._load_error)
                return False
            try:
                self._ef = embedding_functions.SentenceTransformerEmbeddingFunction(
                    model_name=self.model_name
                )
                self._client = chromadb.PersistentClient(path=self.db_path)
                self._songs = self._client.get_collection(
                    SONGS_COLLECTION, embedding_function=self._ef
                )
                logger.info(
                    "MusicIndex ready: %d songs from %s",
                    self._songs.count(), self.db_path,
                )
                return True
            except Exception as e:  # noqa: BLE001 - never break backend startup
                self._load_error = f"failed to open music vector DB: {e}"
                logger.warning("MusicIndex disabled: %s", self._load_error)
                return False

    @property
    def available(self) -> bool:
        return self._load()

    def status(self) -> Dict[str, Any]:
        ok = self._load()
        out: Dict[str, Any] = {
            "available": ok,
            "db_path": self.db_path,
            "embed_model": self.model_name,
        }
        if ok and self._songs is not None:
            out["song_count"] = self._songs.count()
        if self._load_error:
            out["error"] = self._load_error
        return out

    # ---------------- querying ----------------

    @staticmethod
    def _build_where(
        language: Optional[str] = None,
        mood: Optional[str] = None,
        era: Optional[str] = None,
        year_min: Optional[int] = None,
        year_max: Optional[int] = None,
    ) -> Optional[Dict[str, Any]]:
        """Chroma metadata filter. Mood is skipped here because moods are stored
        as a comma-joined string, not a list — it is post-filtered instead."""
        clauses: List[Dict[str, Any]] = []
        if language:
            clauses.append({"language": language.strip().title()})
        if era:
            clauses.append({"era": era})
        if year_min is not None:
            clauses.append({"release_year": {"$gte": int(year_min)}})
        if year_max is not None:
            clauses.append({"release_year": {"$lte": int(year_max)}})
        if not clauses:
            return None
        if len(clauses) == 1:
            return clauses[0]
        return {"$and": clauses}

    def search(
        self,
        query: str,
        limit: int = 5,
        language: Optional[str] = None,
        mood: Optional[str] = None,
        era: Optional[str] = None,
        year_min: Optional[int] = None,
        year_max: Optional[int] = None,
    ) -> Dict[str, Any]:
        """Semantic song search. Always returns a dict (never raises)."""
        if not query or not query.strip():
            return {"status": "error", "error": "empty query", "results": []}
        if not self._load():
            return {
                "status": "unavailable",
                "error": self._load_error or "music index unavailable",
                "results": [],
            }

        where = self._build_where(language, mood, era, year_min, year_max)
        # over-fetch when post-filtering by mood so we can still fill `limit`
        n = max(1, int(limit))
        fetch = n * 4 if mood else n

        try:
            res = self._songs.query(
                query_texts=[query],
                n_results=fetch,
                **({"where": where} if where else {}),
            )
        except Exception as e:  # noqa: BLE001
            logger.warning("music search failed: %s", e)
            return {"status": "error", "error": str(e), "results": []}

        results: List[Dict[str, Any]] = []
        ids = (res.get("ids") or [[]])[0]
        metas = (res.get("metadatas") or [[]])[0]
        dists = (res.get("distances") or [[]])[0]
        mood_needle = mood.strip().lower() if mood else None

        for i, _id in enumerate(ids):
            m = metas[i] or {}
            if mood_needle:
                moods = str(m.get("moods", "")).lower()
                if mood_needle not in moods:
                    continue
            dist = dists[i] if i < len(dists) else None
            results.append({
                "song_id": m.get("song_id") or _id,
                "title": m.get("title"),
                "artist": m.get("primary_artist"),
                "artists": m.get("artists"),
                "album": m.get("album"),
                "release_year": m.get("release_year"),
                "language": m.get("language"),
                "moods": m.get("moods"),
                "genres": m.get("genres"),
                "energy_level": m.get("energy_level"),
                "era": m.get("era"),
                "popularity": m.get("popularity"),
                "youtube_url": m.get("youtube_url"),
                # IMPORTANT: false means the URL is a YouTube *search* link, not a
                # verified video id. Dataset policy: never fabricate video ids.
                "youtube_verified": bool(m.get("youtube_verified", False)),
                "score": round(1.0 - dist, 4) if isinstance(dist, (int, float)) else None,
                "distance": round(dist, 4) if isinstance(dist, (int, float)) else None,
            })
            if len(results) >= n:
                break

        return {
            "status": "success",
            "query": query,
            "count": len(results),
            "filters": {
                "language": language, "mood": mood, "era": era,
                "year_min": year_min, "year_max": year_max,
            },
            "results": results,
        }

    def speak_result(self, payload: Dict[str, Any]) -> str:
        """Short natural-language line for the voice/TTS path."""
        if payload.get("status") != "success" or not payload.get("results"):
            return "I could not find a matching song right now."
        top = payload["results"][0]
        line = f"Playing {top.get('title')} by {top.get('artist')}"
        year = top.get("release_year")
        if year:
            line += f", from {year}"
        others = payload["results"][1:3]
        if others:
            alt = ", ".join(f"{o.get('title')}" for o in others)
            line += f". I also found {alt}"
        return line + "."


music_index = MusicIndex()
