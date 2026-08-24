"""
Tests for Jarvis Security Layer.
"""

import logging

from app.security.redaction import RedactingFormatter
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


def test_token_is_bound_to_its_request_id():
    mgr = ConfirmationTokenManager(ttl_seconds=60)
    token = mgr.create_token("s1", "request-1", "call_contact", {"contact": "Alice"})

    assert mgr.validate_and_consume(token.token, session_id="s1", request_id="request-2") is None
    assert mgr.validate_and_consume(token.token, session_id="s1", request_id="request-1") is not None


def test_redacting_formatter_safely_redacts_keys_and_standalone_tokens():
    formatter = RedactingFormatter()
    record1 = logging.LogRecord(
        name="test", level=logging.INFO, pathname="", lineno=0,
        msg="Connecting with api_key=secret12345 to backend", args=(), exc_info=None
    )
    assert "[REDACTED]" in formatter.format(record1)
    assert "secret12345" not in formatter.format(record1)

    record2 = logging.LogRecord(
        name="test", level=logging.INFO, pathname="", lineno=0,
        msg="Using groq key gsk_123456789012345678901234 in header", args=(), exc_info=None
    )
    assert "[REDACTED_API_KEY]" in formatter.format(record2)
    assert "gsk_" not in formatter.format(record2)
