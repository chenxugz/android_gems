#!/bin/bash
# Download model files for Android GEMS
# Run this script once, then push models to device with push_to_device.sh

set -e
cd "$(dirname "$0")"

echo "=== Downloading SD Turbo GGUF (1.9GB) ==="
if [ ! -f "sd_turbo.gguf" ]; then
    pip3 install -q huggingface_hub
    python3 -c "
from huggingface_hub import hf_hub_download
import shutil
path = hf_hub_download('Green-Sky/SD-Turbo-GGUF', 'sd_turbo-f16-q8_0.gguf')
shutil.copy2(path, 'sd_turbo.gguf')
print('Downloaded sd_turbo.gguf')
"
else
    echo "sd_turbo.gguf already exists, skipping"
fi

echo "=== Downloading TAESD (9MB) ==="
if [ ! -f "taesd.safetensors" ]; then
    python3 -c "
from huggingface_hub import hf_hub_download
import shutil
path = hf_hub_download('madebyollin/taesd', 'diffusion_pytorch_model.safetensors')
shutil.copy2(path, 'taesd.safetensors')
print('Downloaded taesd.safetensors')
"
else
    echo "taesd.safetensors already exists, skipping"
fi

echo "=== Downloading Gemma 4 E2B (2.4GB) ==="
if [ ! -f "gemma-4-E2B-it.litertlm" ]; then
    python3 -c "
from huggingface_hub import hf_hub_download
import shutil
path = hf_hub_download('litert-community/gemma-4-E2B-it-litert-lm', 'gemma-4-E2B-it.litertlm')
shutil.copy2(path, 'gemma-4-E2B-it.litertlm')
print('Downloaded gemma-4-E2B-it.litertlm')
"
else
    echo "gemma-4-E2B-it.litertlm already exists, skipping"
fi

ls -lh *.gguf *.safetensors *.litertlm 2>/dev/null

echo ""
echo "Done. Run ./push_to_device.sh to push models to connected Android device."
