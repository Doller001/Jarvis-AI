"""
Log redaction formatter to hide sensitive secrets in logs.
"""

import re
import logging

SECRET_PATTERNS = [
    re.compile(r'(?i)(api[_-]?key|secret|password|bearer|auth[_-]?token)=["\']?([^"\'\s]+)["\']?'),
    re.compile(r'gsk_[A-Za-z0-9_-]{20,}'),
    re.compile(r'sk-or-v1-[A-Za-z0-9_-]{20,}'),
    re.compile(r'AIzaSy[A-Za-z0-9_-]{33}'),
]

class RedactingFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        original = super().format(record)
        redacted = original
        for pattern in SECRET_PATTERNS:
            redacted = pattern.sub(r'\1=[REDACTED]', redacted)
        return redacted
