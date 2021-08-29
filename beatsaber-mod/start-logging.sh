#!/bin/bash
set -eu
SCRIPT_DIR=$(
    cd -- "$(dirname "$0")" >/dev/null 2>&1
    pwd -P
)

TIMESTAMP=$(date +"%m-%d %T.000")
BSPID=""
while [ -z "$BSPID" ]; do
    sleep 0.1
    BSPID=$(adb shell pidof com.beatgames.beatsaber || echo "")
done
# adb logcat -T "$TIMESTAMP" --pid "$BSPID" | grep --line-buffered "QuestHook\|modloader\|AndroidRuntime"
adb logcat -T "$TIMESTAMP" --pid "$BSPID" "QuestHook[vrcapture|v0.1.0]:*" "AndroidRuntime:E" "*:S"
