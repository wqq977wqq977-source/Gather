#!/bin/sh
# Offline checks by default. Pass --live for one explicit external quote smoke test.
set -eu
PROJECT_ROOT=$(CDPATH="" cd -- "$(dirname -- "$0")/.." && pwd)
QUOTE_TOOLS=${GUILONG_TOOLCHAIN:-"$PROJECT_ROOT/.local/toolchain"}
QUOTE_JAVA_HOME=${GUILONG_JAVA_HOME:-"$QUOTE_TOOLS/jdk-17.0.20.1+1-jre/Contents/Home"}
QUOTE_TEST_DIR=$(mktemp -d "${TMPDIR:-/tmp}/guilong-quotes.XXXXXX")
trap 'rm -rf "$QUOTE_TEST_DIR"' EXIT HUP INT TERM
"$QUOTE_JAVA_HOME/bin/java" -jar "$QUOTE_TOOLS/ecj.jar" -8 -encoding UTF-8 -cp "$QUOTE_TOOLS/platform/android.jar" -d "$QUOTE_TEST_DIR" \
	"$PROJECT_ROOT/app/src/main/java/com/qianwang/privatewealth/QuoteClient.java" \
	"$PROJECT_ROOT/app/src/main/java/com/qianwang/privatewealth/BackupCodec.java" \
	"$PROJECT_ROOT/app/src/main/java/com/qianwang/privatewealth/WealthStore.java" \
	"$PROJECT_ROOT/app/src/main/java/com/qianwang/privatewealth/AssetSankey.java" \
	"$PROJECT_ROOT/app/src/main/java/com/qianwang/privatewealth/DisplayMoney.java" \
	"$PROJECT_ROOT/tests/QuoteBackupTest.java"
"$QUOTE_JAVA_HOME/bin/java" -cp "$QUOTE_TEST_DIR:$QUOTE_TOOLS/platform/android.jar" QuoteBackupTest "$@"
