#!/usr/bin/env bash
# ============================================================================
# train.sh — train the "Hey Jarvis" custom wake-word classifier.
#
# One-shot on a machine with a GPU + ffmpeg (e.g. Google Colab, a Cloud GPU,
# or your Linux box with an NVIDIA card).
#
# What it does:
#   1. Installs uv (if missing) and livekit-wakeword[train,eval,export]
#   2. Downloads the bundled data (Piper TTS voices, backgrounds, RIRs)
#   3. Generates synthetic positive + adversarial-negative speech
#   4. Augments + extracts features
#   5. Trains the conv-attention classifier (3-phase adaptive)
#   6. Exports hey_jarvis.onnx  (input (1,16,96) -> score (1,1))
#
# Then run ./download_models.sh to drop the frozen models + this classifier
# into the Android app assets/ folder.
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

echo ">> [1/6] Ensure uv + livekit-wakeword"
if ! command -v uv >/dev/null 2>&1; then
  curl -LsSf https://astral.sh/uv/install.sh | sh
  export PATH="$HOME/.local/bin:$PATH"
fi
uv tool install "livekit-wakeword[train,eval,export]" || \
  uv tool upgrade "livekit-wakeword[train,eval,export]"

echo ">> [2/6] Download models & data"
livekit-wakeword setup --config hey_jarvis.yaml

echo ">> [3/6] Generate synthetic speech + adversarial negatives"
livekit-wakeword generate hey_jarvis.yaml

echo ">> [4/6] Augment + extract features"
livekit-wakeword augment hey_jarvis.yaml

echo ">> [5/6] Train (this is the long step — Colab L4 ~30-60 min)"
livekit-wakeword train hey_jarvis.yaml

echo ">> [6/6] Export ONNX classifier"
livekit-wakeword export hey_jarvis.yaml

echo ">> Training complete. Exported model:"
ls -la "${SCRIPT_DIR}/output/hey_jarvis/hey_jarvis.onnx"

echo ">> Next: run ./download_models.sh to place all 3 .onnx files into the app assets."
