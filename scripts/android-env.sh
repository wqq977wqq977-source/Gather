#!/bin/bash
# Source this file; all mutable emulator and ADB state belongs to this project.
GUILONG_PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export ANDROID_SDK_ROOT="${GUILONG_ANDROID_SDK:-$GUILONG_PROJECT_ROOT/.local/android-sdk}"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export ANDROID_USER_HOME="$GUILONG_PROJECT_ROOT/.local/android-home"
export ANDROID_EMULATOR_HOME="$ANDROID_USER_HOME"
export ANDROID_AVD_HOME="$GUILONG_PROJECT_ROOT/.local/avd"
export ANDROID_ADB_SERVER_PORT="${GUILONG_ADB_PORT:-5041}"
export ADB_SERVER_SOCKET="tcp:127.0.0.1:$ANDROID_ADB_SERVER_PORT"
export GUILONG_EMULATOR_PORT="${GUILONG_EMULATOR_PORT:-5580}"
