#!/usr/bin/env bash
# ============================================================================
# download_models.sh
# Fetches the two FROZEN front-end ONNX models required by the JARVIS on-device
# wake-word detector, and (optionally) the trained classifier after training.
#
# These two frozen models are bundled inside the livekit-wakeword Python
# package (src/livekit/wakeword/resources/) and are identical for EVERY wake
# word — they only need to be downloaded ONCE and shipped inside the Android APK.
#   - melspectrogram.onnx  (~1.06 MB) raw 16kHz PCM -> mel spectrogram
#   - embedding_model.onnx (~1.32 MB) 76-frame mel window -> 96-dim embedding
#
# The classifier (hey_jarvis.onnx) is YOUR custom model — produced by train.sh.
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Where the Android app expects the assets.
ASSETS_DIR="${SCRIPT_DIR}/../android/app/src/main/assets/wakeword"
mkdir -p "${ASSETS_DIR}"

BASE_URL="https://raw.githubusercontent.com/livekit/livekit-wakeword/main/src/livekit/wakeword/resources"

echo ">> Downloading frozen front-end models into ${ASSETS_DIR}"
for m in melspectrogram.onnx embedding_model.onnx; do
  if [ -s "${ASSETS_DIR}/$m" ]; then
    echo "   skip (already present): $m"
  else
    echo "   fetching: $m"
    curl -fsSL "${BASE_URL}/$m" -o "${ASSETS_DIR}/$m"
  fi
done

# Copy the trained classifier if it exists in the training output dir.
CLASSIFIER="${SCRIPT_DIR}/output/hey_jarvis/hey_jarvis.onnx"
if [ -s "${CLASSIFIER}" ]; then
  echo ">> Copying trained classifier -> ${ASSETS_DIR}/hey_jarvis.onnx"
  cp "${CLASSIFIER}" "${ASSETS_DIR}/hey_jarvis.onnx"
else
  echo ">> NOTE: trained classifier not found at ${CLASSIFIER}"
  echo "   Run ./train.sh first (or copy hey_jarvis.onnx from Colab into ${ASSETS_DIR}/)."
fi

echo ">> Done. Assets in ${ASSETS_DIR}:"
ls -la "${ASSETS_DIR}"
