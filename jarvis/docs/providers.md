# Jarvis AI — LLM Provider Integration & Model Switching

Jarvis includes a multi-provider LLM Gateway supporting **Groq**, **OpenRouter**, **Google Gemini**, and **Ollama (Local)**.

---

## 🔑 1. Connecting API Keys

Set your provider credentials in the backend environment (`.env` or OS environment variables):

### Groq API Setup
```bash
export GROQ_API_KEY="gsk_..."
```
- **Models Supported**: `llama-3.3-70b-versatile`, `llama-3.1-8b-instant`, `mixtral-8x7b-32768`.

### OpenRouter API Setup
```bash
export OPENROUTER_API_KEY="sk-or-v1-..."
```
- **Models Supported**: `anthropic/claude-3.5-sonnet`, `openai/gpt-4o`, `meta-llama/llama-3.3-70b-instruct`.

### Google Gemini API Setup
```bash
export GEMINI_API_KEY="AIzaSy..."
```
- **Models Supported**: `gemini-1.5-flash`, `gemini-1.5-pro`, `gemini-2.0-flash-exp`.

### Ollama (Local / LAN) Setup
```bash
export OLLAMA_BASE_URL="http://localhost:11434"
```
- **Models Supported**: `llama3`, `mistral`, `qwen2.5`, `codellama`.

---

## 🔍 2. Dynamic Provider Discovery Rules

1. **Unauthenticated Providers are Omitted**: If `validate_key()` returns `False` (e.g. key missing or invalid), the provider is hidden from `/api/v1/providers` and the Android UI.
2. **Circuit Breaker Protection**: If a provider fails repeatedly (3 consecutive timeouts/errors), the `CircuitBreaker` sets the provider state to `OPEN` and routes requests to fallback providers.

---

## 🔄 3. Live Provider & Model Switching

You can switch the active provider and model at runtime without restarting the backend or Android app!

### Switch via REST API

```bash
POST /api/v1/providers/select
Content-Type: application/json

{
  "provider": "gemini",
  "model": "gemini-1.5-flash"
}
```

### Response:
```json
{
  "status": "success",
  "active_selection": {
    "provider": "gemini",
    "model": "gemini-1.5-flash"
  }
}
```
