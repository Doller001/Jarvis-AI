#!/usr/bin/env bash
set -euo pipefail

echo "Starting Jarvis AI Backend Server & WebApp..."
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_DIR="$(cd "$PROJECT_DIR/.." && pwd)"
BACKEND_DIR="$PROJECT_DIR/backend"

if [ -n "${PYTHON_BIN:-}" ]; then
  PYTHON="$PYTHON_BIN"
elif [ -x "$BACKEND_DIR/.venv/bin/python3" ]; then
  PYTHON="$BACKEND_DIR/.venv/bin/python3"
elif [ -x "$REPO_DIR/.venv/bin/python3" ]; then
  PYTHON="$REPO_DIR/.venv/bin/python3"
else
  PYTHON="python3"
fi

cd "$BACKEND_DIR"
exec env PYTHONPATH="$BACKEND_DIR" "$PYTHON" -m uvicorn app.main:app --host 0.0.0.0 --port "${PORT:-8000}"
