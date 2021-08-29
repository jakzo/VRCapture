#!/bin/bash
set -eu
SCRIPT_DIR=$(
    cd -- "$(dirname "$0")" >/dev/null 2>&1
    pwd -P
)

"$SCRIPT_DIR/build.sh"
adb push libs/arm64-v8a/libvrcapture.so /sdcard/Android/data/com.beatgames.beatsaber/files/mods/libvrcapture.so
"$SCRIPT_DIR/restart-game.sh"
if [ "${1:-x}" = "--log" ]; then
    "$SCRIPT_DIR/start-logging.sh"
fi
