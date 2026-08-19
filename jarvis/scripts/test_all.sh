#!/usr/bin/env bash
set -e

echo "=== Running Jarvis Backend Pytest Suite ==="
PYTHONPATH=backend pytest backend/tests/ -v

echo "=== All Jarvis backend tests passed! ==="
