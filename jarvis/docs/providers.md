# Jarvis AI — Multi-Provider LLM Gateway

Supported LLM Providers:
- **Groq**: Low-latency Llama 3 models
- **OpenRouter**: Claude 3.5 Sonnet & multi-model routing
- **Google Gemini**: Gemini 1.5 Flash & Pro
- **Ollama**: Local on-device or LAN LLM models

## Dynamic Discovery Rules
1. Unauthenticated providers (missing API keys) are hidden from the UI.
2. Active provider and model can be switched dynamically without app or backend restarts.
