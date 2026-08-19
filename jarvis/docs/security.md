# Jarvis AI — Security & Single-Use Token Architecture

1. **Single-Use Action Confirmation**: Risky actions (phone calls, SMS messages, WhatsApp) generate 256-bit entropy random tokens using `secrets.token_urlsafe(32)`.
2. **Replay Protection**: Confirmation tokens expire after 300 seconds and are immediately invalidated upon consumption.
3. **Log Redaction**: API keys for Groq, OpenRouter, and Gemini are automatically redacted from logs.
