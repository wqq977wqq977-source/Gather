#!/usr/bin/env python3
"""Run exact-money assertions on the JVM; --serial also tests actual Android persistence.

The optional Android test uses an isolated /data/local/tmp folder, never the app's data.
It requires an explicitly selected emulator/device serial and does not install an APK.
"""

import argparse
import os
import shutil
import subprocess
import tempfile
import uuid
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_TOOLS = ROOT / ".local/toolchain"


def run(*args):
    subprocess.run([str(arg) for arg in args], check=True, cwd=ROOT)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--serial", help="Explicit Android device for persistence tests"
    )
    parser.add_argument("--adb", default=shutil.which("adb"), help="Path to adb")
    parser.add_argument(
        "--adb-port",
        type=int,
        default=5037,
        help="ADB server port (project emulator: 5041)",
    )
    options = parser.parse_args()
    tools = Path(
        os.environ.get(
            "ANDROID_TOOLS_HOME",
            os.environ.get("GUILONG_TOOLCHAIN", str(DEFAULT_TOOLS)),
        )
    )
    java_home = Path(
        os.environ.get(
            "GUILONG_JAVA_HOME", str(tools / "jdk-17.0.20.1+1-jre/Contents/Home")
        )
    )
    java = java_home / "bin/java"
    platform = tools / "platform/android.jar"
    with tempfile.TemporaryDirectory(prefix="guilong-domain-") as folder:
        work = Path(folder)
        classes = work / "classes"
        classes.mkdir()
        run(
            java,
            "-jar",
            tools / "ecj.jar",
            "-8",
            "-encoding",
            "UTF-8",
            "-cp",
            platform,
            "-d",
            classes,
            ROOT / "app/src/main/java/com/qianwang/privatewealth/WealthStore.java",
            ROOT / "scripts/WealthDomainTests.java",
        )
        run(
            java,
            "-cp",
            str(classes) + os.pathsep + str(platform),
            "com.qianwang.privatewealth.WealthDomainTests",
        )
        if options.serial:
            if not options.adb:
                raise SystemExit("--adb is required when adb is not in PATH")
            dex = work / "dex"
            dex.mkdir()
            run(
                java,
                "-cp",
                tools / "build-tools/android-15/lib/d8.jar",
                "com.android.tools.r8.D8",
                "--min-api",
                "26",
                "--lib",
                platform,
                "--output",
                dex,
                *sorted(classes.rglob("*.class")),
            )
            archive = work / "tests.jar"
            with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as output:
                for path in dex.glob("*.dex"):
                    output.write(path, path.name)
            remote = "/data/local/tmp/guilong-domain-" + uuid.uuid4().hex
            adb = [options.adb, "-P", str(options.adb_port), "-s", options.serial]
            try:
                run(*adb, "shell", "mkdir", "-p", remote)
                run(*adb, "push", archive, remote + "/tests.jar")
                run(
                    *adb,
                    "shell",
                    "app_process",
                    "-Djava.class.path=" + remote + "/tests.jar",
                    "/system/bin",
                    "com.qianwang.privatewealth.WealthDomainTests",
                    "--storage",
                    remote + "/ledger",
                )
            finally:
                run(*adb, "shell", "rm", "-rf", remote)


if __name__ == "__main__":
    main()
