# JARVIS AI — BACKEND API CONTRACT & PROTOCOL SPECIFICATION

> **Base URL:** `https://jarvis-ai-59qd.onrender.com`  
> **WebSocket URL:** `wss://jarvis-ai-59qd.onrender.com/ws`  
> **Protocol Version:** 1.0.0  

---

## 1. System Health & Probes

### `GET /health/live`
- **Purpose**: Process liveness probe for orchestrators and container health.
- **Auth**: None (Public).
- **Response**: `200 OK`
  ```json
  {"status": "alive", "service": "jarvis-backend", "version": "1.0.0"}
  ```

### `GET /health/ready`
- **Purpose**: Readiness probe validating database connectivity.
- **Auth**: None (Public).
- **Response**: `200 OK`
  ```json
  {"status": "ready", "service": "jarvis-backend", "database": "connected"}
  ```

### `GET /api/v1/health`
- **Purpose**: Lightweight client ping and latency probe.
- **Auth**: None (Public).
- **Response**: `200 OK`
  ```json
  {"status": "healthy", "service": "jarvis-backend", "api_version": "v1", "timestamp_ms": 1787923694000}
  ```

---

## 2. Authentication & Device Identity

### `POST /api/v1/auth/token`
- **Purpose**: Register or authenticate a device, issuing access and refresh tokens.
- **Auth**: None.
- **Request Body**:
  ```json
  {
    "device_name": "Pixel 8 Pro",
    "device_model": "GPJ41",
    "os_version": "Android 14",
    "device_id": "optional-client-guid"
  }
  ```
- **Response**: `200 OK`
  ```json
  {
    "access_token": "eyJhbGciOiJIUzI1Ni...",
    "refresh_token": "eyJhbGciOiJIUzI1Ni...",
    "expires_in": 900,
    "token_type": "bearer",
    "device_id": "bb61c9bea6c054a27926081",
    "trusted": false
  }
  ```

### `POST /api/v1/auth/refresh`
- **Purpose**: Rotate refresh token and issue a fresh access token.
- **Auth**: None (Body contains refresh token).
- **Request Body**:
  ```json
  {
    "refresh_token": "eyJhbGciOiJIUzI1Ni..."
  }
  ```
- **Response**: `200 OK` (Rotated tokens)
  ```json
  {
    "access_token": "eyJhbGciOiJIUzI1Ni...",
    "refresh_token": "eyJhbGciOiJIUzI1Ni...",
    "expires_in": 900,
    "token_type": "bearer",
    "device_id": "bb61c9bea6c054a27926081",
    "trusted": false
  }
  ```
- **Error**: `401 Unauthorized` (`{"error": {"code": "AUTH_REFRESH_INVALID", "message": "Invalid or reused refresh token"}}`)

---

## 3. Conversational AI & Execution Endpoints

### `POST /api/v1/chat`
- **Purpose**: Native Android device chat & action execution endpoint.
- **Auth**: `Bearer <access_token>`.
- **Request Body**:
  ```json
  {
    "text": "turn on flashlight",
    "session_id": "device_session_123",
    "request_id": "req-987"
  }
  ```
- **Response**: `200 OK`
  ```json
  {
    "type": "command_result",
    "request_id": "req-987",
    "session_id": "device_session_123",
    "status": "success",
    "action": "toggle_torch",
    "parameters": {"state": "on"},
    "response_text": "Flashlight turned on.",
    "execution_result": {"success": true, "status": "executed"}
  }
  ```

### `POST /v1/chat/completions` (OpenAI Compatible)
- **Purpose**: Standard OpenAI-compatible completions for external SDKs and dashboards.
- **Auth**: Optional `Bearer <access_token>`.
- **Request Body**:
  ```json
  {
    "model": "nvidia/llama-3.1-nemotron-70b-instruct",
    "messages": [
      {"role": "user", "content": "What is quantum computing?"}
    ]
  }
  ```
- **Response**: `200 OK` (OpenAI Chat Completion Object)

---

## 4. Provider Discovery

### `GET /api/v1/providers`
- **Purpose**: List available LLM providers and operational status.
- **Auth**: Optional `Bearer <access_token>`.
- **Response**: `200 OK`
  ```json
  [
    {"provider": "nvidia", "status": "ready", "models": ["nvidia/llama-3.1-nemotron-70b-instruct"]},
    {"provider": "groq", "status": "ready", "models": ["groq/llama-3.3-70b-versatile"]},
    {"provider": "openrouter", "status": "ready", "models": ["meta-llama/llama-3.3-70b-instruct"]},
    {"provider": "gemini", "status": "ready", "models": ["gemini-1.5-flash"]},
    {"provider": "ollama", "status": "unavailable", "models": []}
  ]
  ```

---

## 5. Standard Error Format

All error responses strictly follow the canonical error envelope:
```json
{
  "error": {
    "code": "AUTH_EXPIRED",
    "message": "Access token expired. Please refresh.",
    "request_id": "req-12345"
  }
}
```
Standard Error Codes:
- `AUTH_MISSING`, `AUTH_INVALID`, `AUTH_EXPIRED`, `AUTH_DEVICE_UNKNOWN`, `AUTH_REFRESH_INVALID`
- `BACKEND_UNAVAILABLE`, `BACKEND_NOT_READY`, `BACKEND_TIMEOUT`
- `PROVIDER_UNAVAILABLE`, `PROVIDER_TIMEOUT`, `PROVIDER_RATE_LIMITED`
- `VALIDATION_ERROR`, `INTERNAL_ERROR`
