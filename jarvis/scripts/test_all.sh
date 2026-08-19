#!/usr/bin/env bash
set -e

echo "=== Running Jarvis Backend Pytest Suite ==="
PYTHONPATH=jarvis/backend /home/saif/Downloads/raphael-ai-assistant-main/.venv/bin/pytest jarvis/backend/tests/ -v

echo "=== All Jarvis backend tests passed! ==="

