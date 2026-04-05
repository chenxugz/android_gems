#!/bin/bash
# Push model files to connected Android device via ADB
# Files go to the app's external files dir (same location as in-app downloads)
set -e
cd "$(dirname "$0")"

APP_EXT_DIR="/storage/emulated/0/Android/data/com.gems.android/files/models"

echo "=== Creating model directory on device ==="
adb shell "mkdir -p $APP_EXT_DIR"

push_model() {
    local file=$1
    if [ ! -f "$file" ]; then
        echo "  SKIP: $file not found"
        return
    fi
    echo "Pushing $(basename $file) ($(du -h "$file" | cut -f1))..."
    adb push "$file" "$APP_EXT_DIR/"
}

push_model "sd_turbo.gguf"
push_model "taesd.safetensors"
push_model "gemma-4-E2B-it.litertlm"
push_model "gemma-4-E4B-it.litertlm"

echo ""
echo "=== Models on device ==="
adb shell "ls -lh $APP_EXT_DIR/" 2>/dev/null || echo "  (empty)"
echo "Done."
