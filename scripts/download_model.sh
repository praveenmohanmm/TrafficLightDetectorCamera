#!/usr/bin/env bash
# Downloads the EfficientDet-Lite0 TFLite model (COCO, int8, ~4.5 MB)
# and places it in the app assets directory so it is bundled into the APK.
#
# Run this once before building:
#   bash scripts/download_model.sh

set -euo pipefail

ASSETS_DIR="app/src/main/assets"
MODEL_FILE="$ASSETS_DIR/efficientdet_lite0.tflite"
MODEL_URL="https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/int8/1/efficientdet_lite0.tflite"

mkdir -p "$ASSETS_DIR"

if [ -f "$MODEL_FILE" ]; then
  echo "Model already present: $MODEL_FILE"
  exit 0
fi

echo "Downloading EfficientDet-Lite0 from MediaPipe model zoo…"
curl -fL --progress-bar -o "$MODEL_FILE" "$MODEL_URL"
echo "Saved to $MODEL_FILE"
echo "Model size: $(du -sh "$MODEL_FILE" | cut -f1)"
