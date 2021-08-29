#!/bin/bash
set -eu
SCRIPT_DIR=$(
    cd -- "$(dirname "$0")" >/dev/null 2>&1
    pwd -P
)

NDK_PATH=$(cat "$SCRIPT_DIR/ndkpath.txt")
BUILD_SCRIPT="$NDK_PATH/build/ndk-build"

"$BUILD_SCRIPT" NDK_PROJECT_PATH="$SCRIPT_DIR" APP_BUILD_SCRIPT="$SCRIPT_DIR/Android.mk" NDK_APPLICATION_MK="$SCRIPT_DIR/Application.mk"
