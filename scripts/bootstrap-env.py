#!/usr/bin/env python3
"""Download a portable JDK 17 / Gradle 8.9 / Android SDK 35 into D:\\LumaLink\\.dev.

This script is intentionally self-contained: the DSH shell cannot use schannel
TLS, but the bundled Python can. It writes only below --root.
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import time
import urllib.request
import zipfile
from pathlib import Path

DOWNLOADS = {
    "jdk": (
        "https://aka.ms/download-jdk/microsoft-jdk-17.0.13-windows-x64.zip",
        "microsoft-jdk-17.0.13-windows-x64.zip",
    ),
    "gradle": (
        "https://services.gradle.org/distributions/gradle-8.9-bin.zip",
        "gradle-8.9-bin.zip",
    ),
    "cmdline-tools": (
        "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip",
        "commandlinetools-win-11076708_latest.zip",
    ),
}

SDK_PACKAGES = ["platform-tools", "platforms;android-35", "build-tools;35.0.0"]


def log(message: str) -> None:
    print(f"[bootstrap] {message}", flush=True)


def download(url: str, dest: Path) -> Path:
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists() and dest.stat().st_size > 8 * 1024 * 1024:
        log(f"reuse {dest.name} ({dest.stat().st_size / 1048576:.1f} MB)")
        return dest
    tmp = dest.with_name(dest.name + ".part")
    log(f"download {url}")
    request = urllib.request.Request(url, headers={"User-Agent": "LumaLink-bootstrap/0.1"})
    with urllib.request.urlopen(request, timeout=90) as response, tmp.open("wb") as handle:
        total = int(response.headers.get("Content-Length") or 0)
        done = 0
        last_report = time.time()
        while True:
            chunk = response.read(1024 * 256)
            if not chunk:
                break
            handle.write(chunk)
            done += len(chunk)
            if time.time() - last_report >= 5:
                if total:
                    log(f"{dest.name}: {done * 100.0 / total:.1f}% ({done / 1048576:.1f} MB)")
                else:
                    log(f"{dest.name}: {done / 1048576:.1f} MB")
                last_report = time.time()
    tmp.replace(dest)
    log(f"saved {dest.name} ({dest.stat().st_size / 1048576:.1f} MB)")
    return dest


def extract_single_root(zip_path: Path, parent: Path, final_name: str) -> Path:
    """Extract an archive that contains exactly one top-level directory."""
    parent.mkdir(parents=True, exist_ok=True)
    staging = parent / f".{final_name}.extract"
    if staging.exists():
        shutil.rmtree(staging)
    staging.mkdir(parents=True)
    with zipfile.ZipFile(zip_path) as archive:
        archive.extractall(staging)
    roots = [entry for entry in staging.iterdir() if entry.is_dir()]
    if len(roots) != 1:
        raise RuntimeError(f"unexpected archive layout in {zip_path.name}: {[p.name for p in roots]}")
    target = parent / final_name
    if target.exists():
        shutil.rmtree(target)
    roots[0].rename(target)
    shutil.rmtree(staging)
    log(f"extracted {zip_path.name} -> {target}")
    return target


def install_cmdline_tools(zip_path: Path, sdk: Path) -> Path:
    latest = sdk / "cmdline-tools" / "latest"
    if (latest / "bin" / "sdkmanager.bat").exists():
        log(f"reuse cmdline-tools -> {latest}")
        return latest
    staging = sdk / ".cmdline-tools.extract"
    if staging.exists():
        shutil.rmtree(staging)
    staging.mkdir(parents=True)
    with zipfile.ZipFile(zip_path) as archive:
        archive.extractall(staging)
    source = staging / "cmdline-tools"
    if not source.exists():
        raise RuntimeError("cmdline-tools archive has no cmdline-tools directory")
    if latest.exists():
        shutil.rmtree(latest)
    latest.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(str(source), str(latest))
    shutil.rmtree(staging)
    log(f"extracted cmdline-tools -> {latest}")
    return latest


def run_sdkmanager(sdkmanager: Path, sdk: Path, env: dict[str, str], args: list[str]) -> int:
    quoted = " ".join(f'"{arg}"' for arg in args)
    command = f'"{sdkmanager}" --sdk_root="{sdk}" {quoted}'
    log(f"run {command}")
    completed = subprocess.run(
        command,
        shell=True,
        env=env,
        input="y\n" * 250,
        text=True,
    )
    return completed.returncode


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=r"D:\LumaLink")
    args = parser.parse_args()

    root = Path(args.root)
    if not root.exists():
        raise SystemExit(f"root does not exist: {root}")

    dev = root / ".dev"
    downloads = dev / "downloads"
    toolchain = dev
    sdk = dev / "android-sdk"
    for directory in (downloads, toolchain, sdk):
        directory.mkdir(parents=True, exist_ok=True)

    jdk_zip = download(DOWNLOADS["jdk"][0], downloads / DOWNLOADS["jdk"][1])
    gradle_zip = download(DOWNLOADS["gradle"][0], downloads / DOWNLOADS["gradle"][1])
    cmdline_zip = download(DOWNLOADS["cmdline-tools"][0], downloads / DOWNLOADS["cmdline-tools"][1])

    jdk = toolchain / "jdk"
    if not (jdk / "bin" / "java.exe").exists():
        extract_single_root(jdk_zip, toolchain, "jdk")
    else:
        log(f"reuse JDK -> {jdk}")

    gradle = toolchain / "gradle"
    if not (gradle / "bin" / "gradle.bat").exists():
        extract_single_root(gradle_zip, toolchain, "gradle")
    else:
        log(f"reuse Gradle -> {gradle}")

    cmdline = install_cmdline_tools(cmdline_zip, sdk)
    sdkmanager = cmdline / "bin" / "sdkmanager.bat"

    env = os.environ.copy()
    env["JAVA_HOME"] = str(jdk)
    env["ANDROID_HOME"] = str(sdk)
    env["ANDROID_SDK_ROOT"] = str(sdk)
    env["PATH"] = (
        f"{jdk / 'bin'};{sdk / 'platform-tools'};{cmdline / 'bin'};"
        + env.get("PATH", "")
    )

    version = subprocess.run(
        [str(jdk / "bin" / "java.exe"), "-version"],
        env=env,
        text=True,
        capture_output=True,
    )
    log((version.stdout or "") + (version.stderr or ""))

    licenses = run_sdkmanager(sdkmanager, sdk, env, ["--licenses"])
    log(f"licenses exit={licenses}")

    packages = run_sdkmanager(sdkmanager, sdk, env, SDK_PACKAGES)
    log(f"packages exit={packages}")
    if packages != 0:
        raise SystemExit(f"sdkmanager failed with exit code {packages}")

    env_ps1 = dev / "env.ps1"
    env_ps1.write_text(
        "# LumaLink portable toolchain\n"
        f"$env:JAVA_HOME = '{jdk}'\n"
        f"$env:ANDROID_HOME = '{sdk}'\n"
        f"$env:ANDROID_SDK_ROOT = '{sdk}'\n"
        f"$env:GRADLE_USER_HOME = '{root / '.gradle-home'}'\n"
        f"$env:ANDROID_USER_HOME = '{root / '.android'}'\n"
        "$env:PATH = \"$env:JAVA_HOME\\bin;$env:ANDROID_HOME\\platform-tools;"
        "$env:ANDROID_HOME\\cmdline-tools\\latest\\bin;$env:PATH\"\n",
        encoding="utf-8",
    )

    required = {
        "java": jdk / "bin" / "java.exe",
        "javac": jdk / "bin" / "javac.exe",
        "gradle": gradle / "bin" / "gradle.bat",
        "sdkmanager": sdkmanager,
        "adb": sdk / "platform-tools" / "adb.exe",
        "android_jar": sdk / "platforms" / "android-35" / "android.jar",
        "aapt2": sdk / "build-tools" / "35.0.0" / "aapt2.exe",
    }
    missing = [name for name, path in required.items() if not path.exists()]
    summary = {
        "root": str(root),
        "jdk": str(jdk),
        "gradle": str(gradle),
        "android_sdk": str(sdk),
        "required": {name: str(path) for name, path in required.items()},
        "missing": missing,
        "licenses_exit": licenses,
        "packages_exit": packages,
    }
    (dev / "install-summary.json").write_text(
        json.dumps(summary, indent=2), encoding="utf-8"
    )
    print(json.dumps(summary, indent=2), flush=True)
    if missing:
        raise SystemExit(f"missing components: {missing}")
    log("toolchain ready")
    return 0


if __name__ == "__main__":
    sys.exit(main())
