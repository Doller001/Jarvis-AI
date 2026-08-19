#!/usr/bin/env bash
set -e

echo "Starting Jarvis AI Backend..."
export PYTHONPATH=backend
uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8000} --reload
