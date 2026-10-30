#!/usr/bin/env python3
"""Build the native Java app offline without changing the shared Android tools.

Override ANDROID_TOOLS_HOME to use a matching standalone toolchain directory.
Only this project receives generated classes, APKs, or signing state.
"""

import argparse
import hashlib
import json
import os
import secrets
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_TOOLS = ROOT / ".local/toolchain"
PACKAGE = "com.qianwang.privatewealth"
ANDROID_NS = "http://schemas.android.com/apk/res/android"


def run(*args):
    subprocess.run([str(value) for value in args], check=True, cwd=ROOT)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check-tools", action="store_true", help="Check inputs without building"
    )
    parser.add_argument(
        "--release",
        action="store_true",
        help="Build a non-debuggable signed release APK",
    )
    options = parser.parse_args()
    local_tools = ROOT / ".local/toolchain"
    fallback_tools = local_tools if local_tools.is_dir() else DEFAULT_TOOLS
    tools = Path(
        os.environ.get(
            "ANDROID_TOOLS_HOME",
            os.environ.get("GUILONG_TOOLCHAIN", str(fallback_tools)),
        )
    )
    java_home = Path(
        os.environ.get(
            "GUILONG_JAVA_HOME", str(tools / "jdk-17.0.20.1+1-jre/Contents/Home")
        )
    )
    java = java_home / "bin/java"
    android = tools / "build-tools/android-15"
    platform = tools / "platform/android.jar"
    required = [
        java,
        java_home / "bin/keytool",
        tools / "ecj.jar",
        platform,
        android / "aapt",
        android / "zipalign",
        android / "lib/d8.jar",
        android / "lib/apksigner.jar",
    ]
    missing = [str(path) for path in required if not path.is_file()]
    if missing:
        raise SystemExit(
            "Missing build inputs:\n"
            + "\n".join(missing)
            + "\nSet ANDROID_TOOLS_HOME or open this project in Android Studio."
        )
    if options.check_tools:
        for path in required:
            print(path)
        run(java, "-version")
        run(java, "-jar", tools / "ecj.jar", "-version")
        return

    source = ROOT / "app/src/main"
    sources = sorted((source / "java").rglob("*.java"))
    if not sources:
        raise SystemExit("No Java sources found in app/src/main/java.")
    work = ROOT / "app/build/local"
    work.mkdir(parents=True, exist_ok=True)
    for name in ("classes", "generated", "dex"):
        target = work / name
        if target.exists():
            shutil.rmtree(target)
        target.mkdir()

    # AGP owns the namespace; add it only to the standalone builder's manifest.
    ET.register_namespace("android", ANDROID_NS)
    manifest = ET.parse(source / "AndroidManifest.xml")
    manifest.getroot().set("package", PACKAGE)
    application = manifest.getroot().find("application")
    application.set(
        "{" + ANDROID_NS + "}debuggable", "false" if options.release else "true"
    )
    local_manifest = work / "AndroidManifest.xml"
    manifest.write(local_manifest, encoding="utf-8", xml_declaration=True)
    unsigned = work / "unsigned.apk"
    assets = source / "assets"
    asset_options = ["-A", assets] if assets.is_dir() else []
    run(
        android / "aapt",
        "package",
        "-f",
        "-m",
        "-J",
        work / "generated",
        "-M",
        local_manifest,
        "-S",
        source / "res",
        *asset_options,
        "-I",
        platform,
        "--min-sdk-version",
        "26",
        "--target-sdk-version",
        "35",
        "--version-code",
        "9",
        "--version-name",
        "0.7.1",
        "-F",
        unsigned,
    )
    sources.extend(sorted((work / "generated").rglob("*.java")))
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
        work / "classes",
        *sources,
    )
    classes = sorted((work / "classes").rglob("*.class"))
    run(
        java,
        "-cp",
        android / "lib/d8.jar",
        "com.android.tools.r8.D8",
        "--release" if options.release else "--debug",
        "--min-api",
        "26",
        "--lib",
        platform,
        "--output",
        work / "dex",
        *classes,
    )
    with zipfile.ZipFile(unsigned, "a", zipfile.ZIP_DEFLATED) as apk:
        for dex in sorted((work / "dex").glob("*.dex")):
            apk.write(dex, dex.name)
    aligned = work / "aligned.apk"
    run(android / "zipalign", "-f", "4", unsigned, aligned)

    signing = ROOT / ".local/signing"
    signing.mkdir(parents=True, exist_ok=True)
    signing.chmod(0o700)
    key = signing / "guilong-debug.p12"
    password = signing / "password.txt"
    if key.exists() != password.exists():
        raise SystemExit(
            "Incomplete signing identity. Restore both .local/signing files together."
        )
    if not key.exists():
        with os.fdopen(
            os.open(password, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "w"
        ) as stream:
            stream.write(secrets.token_urlsafe(32))
        run(
            java_home / "bin/keytool",
            "-genkeypair",
            "-keystore",
            key,
            "-storetype",
            "PKCS12",
            "-storepass:file",
            password,
            "-alias",
            "guilong-debug",
            "-keyalg",
            "RSA",
            "-keysize",
            "3072",
            "-validity",
            "10000",
            "-dname",
            "CN=Guilong Local Debug",
        )
        key.chmod(0o600)
    candidate = work / "signed.apk"
    run(
        java,
        "-jar",
        android / "lib/apksigner.jar",
        "sign",
        "--ks",
        key,
        "--ks-pass",
        "file:" + str(password),
        "--ks-key-alias",
        "guilong-debug",
        "--out",
        candidate,
        aligned,
    )
    run(java, "-jar", android / "lib/apksigner.jar", "verify", "--verbose", candidate)
    variant = "release" if options.release else "debug"
    output = ROOT / f"app/build/outputs/apk/{variant}/Gather-0.7.1-{variant}.apk"
    output.parent.mkdir(parents=True, exist_ok=True)
    os.replace(candidate, output)
    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    report = {
        "applicationId": PACKAGE,
        "versionName": "0.7.1",
        "minSdk": 26,
        "targetSdk": 35,
        "compileSdk": 35,
        "bytes": output.stat().st_size,
        "sha256": digest,
        "build": f"offline-java-{variant}",
        "runtimeValidated": False,
    }
    output.with_suffix(".json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    )
    print("\nAPK: " + str(output))
    print("SHA-256: " + digest)


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as error:
        sys.exit(error.returncode)
