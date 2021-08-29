#!/bin/bash
set -eu
SCRIPT_DIR=$(
    cd -- "$(dirname "$0")" >/dev/null 2>&1
    pwd -P
)

# Builds a .zip file for loading with BMBF
"$SCRIPT_DIR/build.sh"
zip ./vrcapture_v0.1.0.zip ./libs/arm64-v8a/libvrcapture.so ./libs/arm64-v8a/libbeatsaber-hook_*.so ./bmbfmod.json
