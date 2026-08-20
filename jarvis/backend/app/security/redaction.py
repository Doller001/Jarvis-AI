"""
Log redaction formatter to hide sensitive secrets in logs.
"""

import re
import logging

KEY_VALUE_PATTERN = re.compile(r'(?i)(api[_-]?key|secret|password|bearer|auth[_-]?token)=["\']?([^"\'\s]+)["\']?')
TOKEN_PATTERNS = [
    re.compile(r'gsk_[A-Za-z0-9_-]{20,}'),
    re.compile(r'sk-or-v1-[A-Za-z0-9_-]{20,}'),
    re.compile(r'AIzaSy[A-Za-z0-9_-]{33}'),
    re.compile(r'nvapi-[A-Za-z0-9_-]{20,}'),
]


class RedactingFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        original = super().format(record)
        redacted = KEY_VALUE_PATTERN.sub(r'\1=[REDACTED]', original)
        for pattern in TOKEN_PATTERNS:
            redacted = pattern.sub('[REDACTED_API_KEY]', redacted)
        return redacted
