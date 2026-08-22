#!/usr/bin/env bash
set -euo pipefail

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

# Keep test conversations out of the runtime database and avoid SQLite locks
# when the backend is running at the same time.
TEST_DB="$(mktemp "${TMPDIR:-/tmp}/jarvis-tests-XXXXXX.sqlite3")"
trap 'rm -f "$TEST_DB"' EXIT

echo "=== Running Jarvis Backend Pytest Suite ==="
JARVIS_DB_PATH="$TEST_DB" PYTHONPATH="$BACKEND_DIR" "$PYTHON" -m pytest "$BACKEND_DIR/tests" -v

echo "=== All Jarvis backend tests passed! ==="
