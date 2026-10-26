#!/usr/bin/env python3
"""Verify allocation conservation without reading or changing a ledger."""

import os
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOOLS = Path(os.environ.get("ANDROID_TOOLS_HOME", str(ROOT / ".local/toolchain")))
JAVA = TOOLS / "jdk-17.0.20.1+1-jre/Contents/Home/bin/java"
with tempfile.TemporaryDirectory(prefix="guilong-sankey-") as tmp:
    sources = [
        ROOT / "app/src/main/java/com/qianwang/privatewealth" / name
        for name in ("WealthStore.java", "AssetSankey.java")
    ]
    subprocess.run(
        [
            str(JAVA),
            "-jar",
            str(TOOLS / "ecj.jar"),
            "-8",
            "-encoding",
            "UTF-8",
            "-cp",
            str(TOOLS / "platform/android.jar"),
            "-d",
            tmp,
            *map(str, sources),
            str(ROOT / "tests/AssetSankeyTest.java"),
        ],
        check=True,
    )
    subprocess.run(
        [
            str(JAVA),
            "-cp",
            tmp + os.pathsep + str(TOOLS / "platform/android.jar"),
            "com.qianwang.privatewealth.AssetSankeyTest",
        ],
        check=True,
    )
