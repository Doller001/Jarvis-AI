# Jarvis AI — Deployment & Infrastructure Guide

This guide details how to deploy the Jarvis backend on **Docker**, **Render Cloud**, **Linux Servers (systemd)**, and **Windows Workstations**.

---

## 🔑 Environment Variables Reference

Create a `.env` file or set environment variables in your deployment platform:

```ini
# Server Port & Database
PORT=8000
JARVIS_DB_PATH=jarvis_memory.db
DATABASE_URL=sqlite:///jarvis_memory.db   # Or postgresql://user:pass@localhost:5432/jarvis_db

# CORS Configuration
ALLOWED_ORIGINS=http://localhost:3000,http://127.0.0.1:3000,*

# LLM Provider API Keys (Set keys to enable providers)
GROQ_API_KEY=gsk_your_groq_api_key_here
OPENROUTER_API_KEY=sk-or-v1-your_openrouter_key_here
GEMINI_API_KEY=AIzaSy_your_gemini_key_here
OLLAMA_BASE_URL=http://localhost:11434

# Optional Authentication Token for WebSockets
JARVIS_WS_AUTH_TOKEN=your_secure_random_token
```

---

## ☁️ 1. Deploying on Render using Docker

Render provides free Docker web service hosting with automatic HTTPS and WebSocket support.

### Step-by-Step Render Deployment:

1. **Connect GitHub Repository**:
   - Go to [Render Dashboard](https://dashboard.render.com/).
   - Click **New +** → **Blueprint** or **Web Service**.
   - Connect your repository: `https://github.com/Minaty001/and9`.

2. **Render automatic detection**:
   - Render automatically reads `render.yaml` or you can manually select **Docker** as the Runtime.
   - Set **Dockerfile Path**: `backend/Dockerfile`
   - Set **Docker Context**: `backend`

3. **Configure Environment Variables**:
   In Render Dashboard under **Environment**:
   - `GROQ_API_KEY`: Your Groq API key
   - `OPENROUTER_API_KEY`: Your OpenRouter API key
   - `GEMINI_API_KEY`: Your Gemini API key
   - `ALLOWED_ORIGINS`: `*`

4. **Verify Render Deployment**:
   - Render gives you a public URL (e.g. `https://jarvis-backend.onrender.com`).
   - Check health: `https://jarvis-backend.onrender.com/health` → `{"status": "healthy"}`
   - WebSocket URL: `wss://jarvis-backend.onrender.com/ws`

---

## 🐳 2. Local & Server Docker Deployment

### Building & Running with Docker

```bash
cd jarvis/backend

# Build the Docker image
docker build -t jarvis-backend:latest .

# Run container detached on port 8000
docker run -d \
  --name jarvis-brain \
  -p 8000:8000 \
  -e GROQ_API_KEY="gsk_..." \
  -e OPENROUTER_API_KEY="sk-or-v1-..." \
  -e GEMINI_API_KEY="AIzaSy..." \
  --restart unless-stopped \
  jarvis-backend:latest
```

---

## 🐧 3. Linux Server Deployment (Ubuntu / Debian systemd)

### Step 1: Install Python & Virtual Environment

```bash
sudo apt update && sudo apt install -y python3 python3-venv python3-pip
cd /opt
sudo git clone https://github.com/Minaty001/and9.git jarvis
cd jarvis/jarvis/backend
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
```

### Step 2: Create systemd Service File

Create `/etc/systemd/system/jarvis.service`:

```ini
[Unit]
Description=Jarvis AI Assistant Backend Service
After=network.target

[Service]
User=www-data
WorkingDirectory=/opt/jarvis/jarvis/backend
Environment="PYTHONPATH=/opt/jarvis/jarvis/backend"
Environment="PORT=8000"
Environment="GROQ_API_KEY=gsk_your_key"
ExecStart=/opt/jarvis/jarvis/backend/.venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8000 --workers 4
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

### Step 3: Enable & Start Service

```bash
sudo systemctl daemon-reload
sudo systemctl enable jarvis
sudo systemctl start jarvis
sudo systemctl status jarvis
```

---

## 🪟 4. Windows Workstation Deployment

### Native Windows PowerShell Setup

1. **Open PowerShell as Administrator**:
   ```powershell
   cd C:\path\to\and9\jarvis\backend
   python -m venv .venv
   .\.venv\Scripts\Activate.ps1
   pip install -r requirements.txt
   ```

2. **Set Environment Variables in PowerShell**:
   ```powershell
   $env:GROQ_API_KEY="gsk_your_key_here"
   $env:OPENROUTER_API_KEY="sk-or-v1-your_key_here"
   $env:GEMINI_API_KEY="AIzaSy_your_key_here"
   $env:PYTHONPATH="."
   ```

3. **Run Server**:
   ```powershell
   uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
   ```

### Running via Docker Desktop on Windows

```powershell
cd C:\path\to\and9\jarvis\backend
docker build -t jarvis-backend .
docker run -d -p 8000:8000 --name jarvis-backend jarvis-backend
```
