"""
Tests for Jarvis music semantic retrieval.

Skips the live-index tests when the music vector DB is not built on this
machine, but ALWAYS tests registration + graceful degradation.
"""

import pytest

from app.retrieval.music_index import MusicIndex, music_index
from app.tools.registry import tool_registry
from app.tools.executor import tool_executor


# ---------- always-on: wiring + graceful degradation ----------

def test_search_music_tool_registered():
    tool = tool_registry.get_tool("search_music")
    assert tool is not None
    assert tool.platform == "backend"
    assert tool.risk_level == "safe"
    assert tool.requires_confirmation is False
    assert "query" in tool.parameters
    assert tool.parameters["query"].required is True
    # optional params must not be forced on the LLM
    assert tool.parameters["mood"].required is False


def test_search_music_in_llm_schemas():
    names = [s["function"]["name"] for s in tool_registry.get_llm_schemas()]
    assert "search_music" in names
    schema = next(s for s in tool_registry.get_llm_schemas()
                  if s["function"]["name"] == "search_music")
    assert schema["function"]["parameters"]["required"] == ["query"]


def test_missing_db_degrades_gracefully(tmp_path):
    """A missing DB must not raise -- Jarvis has to keep working without music."""
    idx = MusicIndex(db_path=str(tmp_path / "nope"))
    assert idx.available is False
    out = idx.search("sad song")
    assert out["status"] == "unavailable"
    assert out["results"] == []
    assert "error" in out
    status = idx.status()
    assert status["available"] is False


@pytest.mark.asyncio
async def test_executor_empty_query_is_error():
    res = await tool_executor.execute_tool("search_music", {"query": "  "})
    assert res["status"] == "error"
    assert res["tool"] == "search_music"


def test_speak_result_handles_no_results():
    line = music_index.speak_result({"status": "unavailable", "results": []})
    assert isinstance(line, str) and line


# ---------- live index tests (skipped if DB absent) ----------

live = pytest.mark.skipif(
    not music_index.available,
    reason="music vector DB not available on this machine",
)


@live
def test_status_reports_songs():
    st = music_index.status()
    assert st["available"] is True
    assert st["song_count"] > 0


@live
def test_semantic_search_returns_relevant_songs():
    out = music_index.search("sad hindi song for late night heartbreak", limit=5)
    assert out["status"] == "success"
    assert out["count"] > 0
    top = out["results"][0]
    assert top["title"]
    assert top["artist"]
    assert 0.0 <= top["score"] <= 1.0
    # a heartbreak query should surface Hindi and/or sad-tagged songs
    joined = " ".join(
        f"{r['language']} {r['moods']}".lower() for r in out["results"]
    )
    assert "hindi" in joined or "sad" in joined


@live
def test_language_filter_is_respected():
    out = music_index.search("love song", limit=5, language="English")
    assert out["status"] == "success"
    assert out["count"] > 0
    assert all(r["language"] == "English" for r in out["results"])


@live
def test_year_range_filter_is_respected():
    out = music_index.search("classic song", limit=5, year_min=1960, year_max=2000)
    assert out["status"] == "success"
    for r in out["results"]:
        assert 1960 <= r["release_year"] <= 2000


@live
def test_mood_filter_is_respected():
    out = music_index.search("song to dance to", limit=5, mood="party")
    assert out["status"] == "success"
    for r in out["results"]:
        assert "party" in r["moods"].lower()


@live
def test_limit_is_honoured():
    out = music_index.search("good song", limit=3)
    assert out["count"] <= 3


@live
@pytest.mark.asyncio
async def test_executor_search_music_end_to_end():
    res = await tool_executor.execute_tool(
        "search_music", {"query": "energetic party banger", "limit": 3}
    )
    assert res["status"] == "success"
    assert res["tool"] == "search_music"
    assert res["count"] > 0
    assert len(res["songs"]) == res["count"]
    # the spoken line must name the top song
    assert res["songs"][0]["title"] in res["result"]
    # youtube policy: verified flag must be present so callers never assume
    assert "youtube_verified" in res["songs"][0]
