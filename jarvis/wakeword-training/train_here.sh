#!/usr/bin/env bash
# ============================================================
#  Train "Hey Jarvis" locally (CPU) with livekit-wakeword 0.2.1
#  Usage:  bash train_here.sh
#  Produces: output/hey_jarvis/hey_jarvis.onnx
#  Then copies the classifier into the Android app assets.
# ============================================================
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENV="$HERE/../../.tmp_train_venv"
cd "$HERE"

if [ ! -d "$VENV" ]; then
  echo "ERROR: venv not found at $VENV — run the training env setup first."
  exit 1
fi
# shellcheck disable=SC1091
source "$VENV/bin/activate"

export PYTHONPATH="$HERE:${PYTHONPATH:-}"

echo "[1/5] Setup external data (Piper weights, RIRs, MUSAN background)..."
# --skip-acav avoids the 16GB ACAV100M download (validation features only).
python -m livekit.wakeword setup --config hey_jarvis.yaml --skip-acav || \
  echo "  (setup warnings are non-fatal; continuing)"

echo "[2/5] Generate synthetic speech (positive + adversarial negatives)..."
python -m livekit.wakeword generate hey_jarvis.yaml

echo "[3/5] Augment (noise/RIR mixing) + extract frozen-model features..."
python -m livekit.wakeword augment hey_jarvis.yaml

echo "[4/5] Train classifier..."
python -m livekit.wakeword train hey_jarvis.yaml

echo "[5/5] Export to ONNX + copy into Android assets..."
python -m livekit.wakeword export hey_jarvis.yaml
SRC="$HERE/output/hey_jarvis/hey_jarvis.onnx"
DST="$HERE/../android/app/src/main/assets/wakeword/hey_jarvis.onnx"
if [ -f "$SRC" ]; then
  mkdir -p "$(dirname "$DST")"
  cp "$SRC" "$DST"
  echo "  -> $DST"
else
  echo "  WARNING: $SRC not found — check training output above."
fi

echo "DONE."
