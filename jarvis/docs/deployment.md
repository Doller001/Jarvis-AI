# Jarvis AI — Backend Deployment Guide

## Docker Deployment
```bash
cd backend
docker build -t jarvis-backend:latest .
docker run -d -p 8000:8000 -e GROQ_API_KEY="gsk_..." jarvis-backend:latest
```

Healthcheck endpoint: `/health`
WebSocket endpoint: `/ws`
