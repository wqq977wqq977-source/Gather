#!/bin/bash
set -euo pipefail
source "$(dirname "$0")/android-env.sh"
mkdir -p "$ANDROID_USER_HOME"
exec "$ANDROID_SDK_ROOT/platform-tools/adb" -P "$ANDROID_ADB_SERVER_PORT" \
    -s "emulator-$GUILONG_EMULATOR_PORT" "$@"
