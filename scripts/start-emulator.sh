#!/bin/bash
set -euo pipefail
source "$(dirname "$0")/android-env.sh"
IMAGE_PATH="$ANDROID_SDK_ROOT/system-images/android-35/google_apis/arm64-v8a"
if [[ ! -f "$IMAGE_PATH/system.img" ]]; then
    echo "Missing API 35 ARM64 image: $IMAGE_PATH" >&2
    exit 1
fi
mkdir -p "$ANDROID_USER_HOME" "$ANDROID_AVD_HOME/Guilong.avd"
if [[ ! -f "$ANDROID_AVD_HOME/Guilong.avd/config.ini" ]]; then
    cat > "$ANDROID_AVD_HOME/Guilong.avd/config.ini" <<EOF
AvdId=Guilong
avd.ini.displayname=Guilong - Private Ledger
abi.type=arm64-v8a
hw.cpu.arch=arm64
hw.cpu.ncore=2
hw.ramSize=1536
hw.lcd.width=720
hw.lcd.height=1600
hw.lcd.density=280
hw.gpu.enabled=yes
hw.gpu.mode=auto
hw.keyboard=no
hw.mainKeys=no
hw.audioInput=no
hw.audioOutput=no
hw.camera.back=none
hw.camera.front=none
disk.dataPartition.size=2G
image.sysdir.1=$IMAGE_PATH/
tag.id=google_apis
tag.display=Google APIs
PlayStore.enabled=false
fastboot.forceColdBoot=yes
EOF
fi
if [[ ! -f "$ANDROID_AVD_HOME/Guilong.ini" ]]; then
    cat > "$ANDROID_AVD_HOME/Guilong.ini" <<EOF
avd.ini.encoding=UTF-8
path=$ANDROID_AVD_HOME/Guilong.avd
target=android-35
EOF
fi
"$ANDROID_SDK_ROOT/platform-tools/adb" -P "$ANDROID_ADB_SERVER_PORT" start-server
exec "$ANDROID_SDK_ROOT/emulator/emulator" -avd Guilong \
    -port "$GUILONG_EMULATOR_PORT" -no-snapshot -no-boot-anim -no-metrics \
    -no-audio "$@"
