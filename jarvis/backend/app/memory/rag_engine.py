"""
Retrieval-Augmented Generation (RAG) Semantic & Episodic Memory Engine.
Uses SQLite FTS5 for full-text search with token matching and fallback similarity.
"""

import json
import logging
import os
import re
import sqlite3
import time
import uuid
from typing import Any, Optional

logger = logging.getLogger(__name__)


class RAGEngine:
    """
    RAG Semantic & Episodic Memory Engine utilizing SQLite FTS5 with LIKE fallback.
    """

    def __init__(self, db_path: Optional[str] = None) -> None:
        self.db_path = db_path or os.getenv("JARVIS_DB_PATH", "jarvis_memory.db")
        self.has_fts = True
        self._init_fts()

    def _get_connection(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        return conn

    def _init_fts(self) -> None:
        conn = self._get_connection()
        try:
            with conn:
                conn.execute("""
                    CREATE TABLE IF NOT EXISTS rag_chunks (
                        chunk_id TEXT PRIMARY KEY,
                        session_id TEXT NOT NULL,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        metadata TEXT,
                        timestamp REAL NOT NULL
                    );
                """)
                try:
                    conn.execute("""
                        CREATE VIRTUAL TABLE IF NOT EXISTS rag_chunks_fts USING fts5(
                            chunk_id UNINDEXED,
                            content,
                            tokenize = 'porter ascii'
                        );
                    """)
                except sqlite3.OperationalError as e:
                    logger.warning(f"FTS5 virtual table initialization failed ({e}); falling back to LIKE search.")
                    self.has_fts = False
        finally:
            conn.close()

    def index_chunk(
        self,
        session_id: str,
        content: str,
        role: str = "user",
        metadata: Optional[dict[str, Any]] = None,
    ) -> str:
        """
        Indexes a memory chunk into the database and FTS5 index.
        """
        if not content or not content.strip():
            return ""

        chunk_id = f"chk-{uuid.uuid4().hex[:12]}"
        now = time.time()
        meta_str = json.dumps(metadata) if metadata is not None else "{}"

        conn = self._get_connection()
        try:
            with conn:
                conn.execute(
                    "INSERT INTO rag_chunks (chunk_id, session_id, role, content, metadata, timestamp) VALUES (?, ?, ?, ?, ?, ?)",
                    (chunk_id, session_id, role, content.strip(), meta_str, now),
                )
                if self.has_fts:
                    try:
                        conn.execute(
                            "INSERT INTO rag_chunks_fts (chunk_id, content) VALUES (?, ?)",
                            (chunk_id, content.strip()),
                        )
                    except sqlite3.OperationalError as e:
                        logger.warning(f"FTS5 indexing failed ({e}); disabling FTS5.")
                        self.has_fts = False
        finally:
            conn.close()

        return chunk_id

    def search(
        self,
        query: str,
        session_id: Optional[str] = None,
        top_k: int = 3,
    ) -> list[dict[str, Any]]:
        """
        Searches chunks matching the query using FTS5 match when available, or LIKE fallback.
        """
        if not query or not query.strip() or top_k <= 0:
            return []

        clean_tokens = [re.sub(r"[^a-zA-Z0-9]", "", w) for w in query.split()]
        tokens = [t for t in clean_tokens if t]
        if not tokens:
            return []

        conn = self._get_connection()
        try:
            # 1. Attempt FTS5 Match if enabled
            if self.has_fts:
                fts_query = " OR ".join(tokens)
                try:
                    if session_id:
                        cursor = conn.execute(
                            """
                            SELECT c.chunk_id, c.session_id, c.role, c.content, c.timestamp, f.rank
                            FROM rag_chunks_fts f
                            JOIN rag_chunks c ON f.chunk_id = c.chunk_id
                            WHERE rag_chunks_fts MATCH ? AND c.session_id = ?
                            ORDER BY f.rank LIMIT ?
                            """,
                            (fts_query, session_id, top_k),
                        )
                    else:
                        cursor = conn.execute(
                            """
                            SELECT c.chunk_id, c.session_id, c.role, c.content, c.timestamp, f.rank
                            FROM rag_chunks_fts f
                            JOIN rag_chunks c ON f.chunk_id = c.chunk_id
                            WHERE rag_chunks_fts MATCH ?
                            ORDER BY f.rank LIMIT ?
                            """,
                            (fts_query, top_k),
                        )
                    rows = cursor.fetchall()
                    if rows:
                        return [
                            {
                                "chunk_id": r["chunk_id"],
                                "session_id": r["session_id"],
                                "role": r["role"],
                                "content": r["content"],
                                "score": float(r["rank"]),
                            }
                            for r in rows
                        ]
                except sqlite3.OperationalError:
                    pass

            # 2. Fallback LIKE search
            clauses = ["content LIKE ?"] * len(tokens)
            where_clause = " OR ".join(clauses)
            score_expr = " + ".join(["(CASE WHEN content LIKE ? THEN 1.0 ELSE 0.0 END)"] * len(tokens))

            params: list[Any] = [f"%{t}%" for t in tokens]
            all_params: list[Any] = [f"%{t}%" for t in tokens]
            if session_id:
                where_clause = f"session_id = ? AND ({where_clause})"
                all_params.append(session_id)
            all_params.extend(params)
            all_params.append(top_k)

            cursor = conn.execute(
                f"""
                SELECT chunk_id, session_id, role, content, timestamp, ({score_expr}) AS match_score
                FROM rag_chunks
                WHERE {where_clause}
                ORDER BY match_score DESC, timestamp DESC
                LIMIT ?
                """,
                all_params,
            )
            rows = cursor.fetchall()
            return [
                {
                    "chunk_id": r["chunk_id"],
                    "session_id": r["session_id"],
                    "role": r["role"],
                    "content": r["content"],
                    "score": float(r["match_score"]),
                }
                for r in rows
            ]
        finally:
            conn.close()


rag_engine = RAGEngine()
