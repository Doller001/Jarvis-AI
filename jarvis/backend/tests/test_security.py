"""
Tests for Jarvis Security Layer.
"""

from app.security.token_manager import ConfirmationTokenManager


def test_token_randomness_and_replay_protection():
    mgr = ConfirmationTokenManager(ttl_seconds=60)
    t = mgr.create_token("s1", "r1", "call_contact", {"contact": "Alice"})
    assert len(t.token) >= 32

    # First consumption succeeds
    consumed = mgr.validate_and_consume(t.token, session_id="s1")
    assert consumed is not None
    assert consumed.action == "call_contact"

    # Replay attack fails
    replayed = mgr.validate_and_consume(t.token, session_id="s1")
    assert replayed is None
