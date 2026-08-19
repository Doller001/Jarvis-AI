"""
Utterance text normalizer for Jarvis.
"""

import re

class IntentNormalizer:
    def normalize(self, text: str) -> str:
        if not text:
            return ""
        cleaned = text.lower().strip()
        cleaned = re.sub(r'\s+', ' ', cleaned)
        return cleaned

intent_normalizer = IntentNormalizer()
