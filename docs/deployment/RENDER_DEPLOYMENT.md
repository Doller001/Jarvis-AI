# JARVIS AI — CANONICAL RENDER DEPLOYMENT SPECIFICATION

> **Single Source of Truth Blueprint:** `jarvis/render.yaml`  
> **Production URL:** `https://jarvis-ai-59qd.onrender.com`  
> **WebSocket URL:** `wss://jarvis-ai-59qd.onrender.com/ws`  

---

## 1. Canonical Render Blueprint (`jarvis/render.yaml`)

```yaml
services:
  - type: web
    name: jarvis-backend
    env: docker
    dockerfilePath: backend/Dockerfile
    dockerContext: backend
    region: singapore
    plan: free
    healthCheckPath: /health/ready
    envVars:
      - key: PORT
        value: 8000
      - key: ENVIRONMENT
        value: production
      - key: ALLOWED_ORIGINS
        value: "https://jarvis-ai-59qd.onrender.com,http://localhost:3000,http://127.0.0.1:3000"
      - key: JARVIS_JWT_SECRET
        sync: false
      - key: DATABASE_URL
        sync: false
      - key: REDIS_URL
        sync: false
      - key: NVIDIA_API_KEY
        sync: false
      - key: GROQ_API_KEY
        sync: false
      - key: OPENROUTER_API_KEY
        sync: false
      - key: GEMINI_API_KEY
        sync: false
      - key: OLLAMA_ENABLED
        value: "false"

  - type: redis
    name: jarvis-redis
    plan: free
    ipAllowList: []
```

---

## 2. Canonical Backend Dockerfile (`jarvis/backend/Dockerfile`)

```dockerfile
# Production Multi-Stage Dockerfile for Jarvis AI Backend
FROM python:3.12-slim AS builder

WORKDIR /build

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir --prefix=/install -r requirements.txt

FROM python:3.12-slim AS runner

WORKDIR /app

RUN useradd -m -u 1000 jarvis && chown -R jarvis:jarvis /app

COPY --from=builder /install /usr/local
COPY --chown=jarvis:jarvis app /app/app

USER jarvis

ENV PYTHONPATH=/app \
    PORT=8000 \
    PYTHONUNBUFFERED=1

EXPOSE 8000

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
    CMD python3 -c "import urllib.request; urllib.request.urlopen('http://localhost:' + str(os.environ.get('PORT', 8000)) + '/health/live')" || exit 1

CMD ["sh", "-c", "uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8000}"]
```
