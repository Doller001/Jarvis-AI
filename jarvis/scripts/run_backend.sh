#!/usr/bin/env bash
set -e

echo "Starting Jarvis AI Backend Server & WebApp..."
export PYTHONPATH=jarvis/backend
/home/saif/Downloads/raphael-ai-assistant-main/.venv/bin/python3 -m uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8000}
