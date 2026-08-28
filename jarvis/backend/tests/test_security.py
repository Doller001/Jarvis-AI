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


def test_jwt_manager_and_session_rotation():
    from app.security.jwt_manager import jwt_manager
    from app.security.device_registry import device_registry

    # 1. Register device
    dev = device_registry.register_device("Pixel Test", "GPJ41", "Android 14", "test-dev-1")
    assert dev.device_id == "test-dev-1"

    # 2. Create token pair with session
    session_id = "sess-test-1"
    token_pair = jwt_manager.create_token_pair("test-dev-1", session_id=session_id)
    assert token_pair.access_token is not None
    assert token_pair.refresh_token is not None

    # Record session
    device_registry.create_session(session_id, "test-dev-1", token_pair.refresh_token, expires_at=9999999999)

    # 3. Validate access token
    payload = jwt_manager.validate_token(token_pair.access_token)
    assert payload is not None
    assert payload.sub == "test-dev-1"
    assert payload.token_type == "access"
    assert payload.jti is not None

    # 4. Rotate session
    new_session_id = "sess-test-2"
    new_refresh = jwt_manager.create_refresh_token("test-dev-1", session_id=new_session_id)
    success = device_registry.validate_and_rotate_session(
        old_session_id=session_id,
        new_session_id=new_session_id,
        device_id="test-dev-1",
        old_refresh_token=token_pair.refresh_token,
        new_refresh_token=new_refresh,
        expires_at=9999999999
    )
    assert success is True

    # 5. Replay old token -> must fail
    replay_success = device_registry.validate_and_rotate_session(
        old_session_id=session_id,
        new_session_id="sess-test-3",
        device_id="test-dev-1",
        old_refresh_token=token_pair.refresh_token,
        new_refresh_token="new-refresh-token",
        expires_at=9999999999
    )
    assert replay_success is False

