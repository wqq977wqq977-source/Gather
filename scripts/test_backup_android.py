#!/usr/bin/env python3
"""Build and run isolated Android backup/lifecycle checks on an explicit emulator."""

import argparse
import os
import shutil
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "com.qianwang.privatewealth.backupqa"
NS = "http://schemas.android.com/apk/res/android"


def run(*args, **kwargs):
    return subprocess.run([str(arg) for arg in args], check=True, **kwargs)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="Explicit emulator serial")
    parser.add_argument(
        "--adb", default=str(ROOT / ".local/android-sdk/platform-tools/adb")
    )
    parser.add_argument("--adb-port", type=int, default=5041)
    args = parser.parse_args()
    if not args.serial.startswith("emulator-"):
        raise SystemExit("Use an isolated emulator for synthetic backup checks.")
    work = ROOT / ".local/backup-lifecycle-qa"
    source = work / "app/src/main"
    source.mkdir(parents=True, exist_ok=True)
    for name in ("java", "res", "assets"):
        target = source / name
        if target.exists():
            shutil.rmtree(target)
        shutil.copytree(ROOT / "app/src/main" / name, target)
    test = (
        source / "java/com/qianwang/privatewealth/BackupLifecycleInstrumentation.java"
    )
    shutil.copy2(ROOT / "tests/BackupLifecycleInstrumentation.java", test)
    ET.register_namespace("android", NS)
    manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml")
    for element in manifest.iter():
        name = element.get("{" + NS + "}name", "")
        if name.startswith("."):
            element.set("{" + NS + "}name", "com.qianwang.privatewealth" + name)
    ET.SubElement(
        manifest.getroot(),
        "instrumentation",
        {
            "{"
            + NS
            + "}name": "com.qianwang.privatewealth.BackupLifecycleInstrumentation",
            "{" + NS + "}targetPackage": PACKAGE,
        },
    )
    manifest.write(
        source / "AndroidManifest.xml", encoding="utf-8", xml_declaration=True
    )
    scripts = work / "scripts"
    scripts.mkdir(exist_ok=True)
    builder = (
        (ROOT / "scripts/build_apk.py")
        .read_text()
        .replace('PACKAGE = "com.qianwang.privatewealth"', f'PACKAGE = "{PACKAGE}"')
    )
    (scripts / "build_apk.py").write_text(builder)
    env = dict(os.environ)
    env.setdefault("ANDROID_TOOLS_HOME", str(ROOT / ".local/toolchain"))
    build = work / "app/build"
    if build.exists():
        shutil.rmtree(build)
    run("python3", scripts / "build_apk.py", env=env)
    apks = list((work / "app/build/outputs/apk/debug").glob("*.apk"))
    if len(apks) != 1:
        raise SystemExit("Expected exactly one QA APK")
    adb = [args.adb, "-P", str(args.adb_port), "-s", args.serial]
    try:
        run(*adb, "install", "-r", apks[0])
        result = run(
            *adb,
            "shell",
            "am",
            "instrument",
            "-w",
            PACKAGE + "/com.qianwang.privatewealth.BackupLifecycleInstrumentation",
            capture_output=True,
            text=True,
        )
        print(result.stdout)
        if "PASS " not in result.stdout or "FAIL " in result.stdout:
            raise SystemExit("Android backup/lifecycle checks failed")
    finally:
        run(*adb, "uninstall", PACKAGE)


if __name__ == "__main__":
    main()
