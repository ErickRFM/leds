#!/usr/bin/env python3
"""Build a *locally selected* Arduino ESP32 sketch into a test-only firmware package.

This tool does not listen on a port, upload to hardware, enable OTA, or sandbox
untrusted C++ build hooks. Review the sketch and its libraries before compiling.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROFILE = ROOT / "config" / "esp32-board.env"
OTA_SLOT_BYTES = 0x1E0000  # Audited ESP32 Classic 4MB/min_spiffs profile
MIN_HEADROOM_BYTES = 64 * 1024
MAX_FILES = 200
MAX_SOURCE_BYTES = 2 * 1024 * 1024


class BuildError(Exception):
    pass


def board_profile(path: Path = PROFILE) -> tuple[str, str]:
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        match = re.fullmatch(r"([A-Z0-9_]+)=([A-Za-z0-9_.:=,-]+)", line)
        if not match or match.group(1) in values:
            raise BuildError("Invalid or duplicate board profile entry")
        values[match.group(1)] = match.group(2)
    fqbn = values.get("ARDUINO_FQBN")
    core = values.get("ARDUINO_ESP32_CORE")
    if not fqbn or not core:
        raise BuildError("Incomplete board profile")
    if fqbn != "esp32:esp32:esp32:PartitionScheme=min_spiffs":
        raise BuildError("Board/partition layout is not audited for this builder")
    return fqbn, core


def inspect_sketch(sketch: Path) -> Path:
    directory = sketch.resolve(strict=True)
    if not directory.is_dir():
        raise BuildError("Sketch must be a directory containing matching <name>.ino")
    if not re.fullmatch(r"[A-Za-z0-9_-]{1,64}", directory.name):
        raise BuildError("Sketch directory name must be 1-64 ASCII letters, digits, _ or -")
    expected = directory / (directory.name + ".ino")
    if not expected.is_file() or expected.is_symlink():
        raise BuildError("Missing matching main Arduino .ino file")
    total = 0
    count = 0
    for child in directory.rglob("*"):
        if child.is_symlink():
            raise BuildError("Symlinks are not supported inside sketch projects")
        if child.is_file():
            count += 1
            total += child.stat().st_size
            if count > MAX_FILES or total > MAX_SOURCE_BYTES:
                raise BuildError("Sketch exceeds local project file/size limits")
    return directory


def artifact_manifest(fqbn: str, core: str, sketch_name: str, binaries: list[Path]) -> dict:
    if not binaries:
        raise BuildError("Arduino CLI did not produce any .bin")
    app_name = sketch_name + ".ino.bin"
    app = next((p for p in binaries if p.name == app_name), None)
    if app is None:
        raise BuildError("Expected compiled application .bin not found")
    app_bytes = app.stat().st_size
    if app_bytes == 0 or app_bytes > OTA_SLOT_BYTES - MIN_HEADROOM_BYTES:
        raise BuildError("Application binary does not fit the audited slot with 64 KiB headroom")
    if len({p.name for p in binaries}) != len(binaries):
        raise BuildError("Duplicate binary basenames")
    return {
        "schema_version": 1,
        "kind": "arduino-esp32-unsigned-lab-build",
        "sketch": sketch_name,
        "board_fqbn": fqbn,
        "arduino_esp32_core_expected": core,
        "ota_slot_bytes": OTA_SLOT_BYTES,
        "minimum_headroom_bytes": MIN_HEADROOM_BYTES,
        "application_bytes": app_bytes,
        "firmware_signature_verified": False,
        "automatic_flash_enabled": False,
        "ota_install_authorized": False,
        "physical_hardware_tested": False,
        "files": [
            {"name": p.name, "bytes": p.stat().st_size,
             "sha256": hashlib.sha256(p.read_bytes()).hexdigest()}
            for p in sorted(binaries, key=lambda item: item.name)
        ],
    }


def build(sketch: Path, output: Path, cli: str, timeout: int = 900) -> Path:
    source = inspect_sketch(sketch)
    fqbn, core = board_profile()
    if not (60 <= timeout <= 1800):
        raise BuildError("Timeout must be between 60 and 1800 seconds")
    if not cli or not shutil.which(cli):
        raise BuildError("Arduino CLI not found. Install Arduino CLI and add it to PATH, or pass --arduino-cli")
    destination = output.resolve()
    if destination == source or destination.is_relative_to(source):
        raise BuildError("Build output cannot be placed inside the sketch")
    if destination.exists():
        raise BuildError("Output already exists; choose a new directory to preserve old builds")
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".esp32-build-", dir=destination.parent) as temp:
        workspace = Path(temp)
        generated = workspace / "compiled"
        generated.mkdir()
        command = [cli, "compile", "--fqbn", fqbn, "--output-dir",
                   str(generated), str(source)]
        try:
            result = subprocess.run(command, stdout=subprocess.PIPE,
                                    stderr=subprocess.STDOUT, text=True,
                                    errors="replace", timeout=timeout, check=False)
        except subprocess.TimeoutExpired as error:
            raise BuildError("Arduino compile timed out; no package generated") from error
        if result.returncode != 0:
            raise BuildError("Arduino CLI compile failed (last output):\n"
                             + result.stdout[-12000:])
        binaries = sorted(p for p in generated.iterdir() if p.is_file() and p.suffix == ".bin")
        manifest = artifact_manifest(fqbn, core, source.name, binaries)
        package = workspace / "package"
        package.mkdir()
        for binary in binaries:
            shutil.copy2(binary, package / binary.name)
        (package / "manifest.json").write_text(
            json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        (package / "build.log").write_text(
            result.stdout[-30000:], encoding="utf-8")
        with zipfile.ZipFile(package / "firmware-package.zip", "w",
                             compression=zipfile.ZIP_DEFLATED) as archive:
            for item in sorted(package.glob("*.bin")):
                archive.write(item, arcname=item.name)
            archive.write(package / "manifest.json", arcname="manifest.json")
        package.rename(destination)
        return destination


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sketch", type=Path, required=True,
                        help="LOCAL trusted Arduino sketch folder containing <folder>.ino")
    parser.add_argument("--output", type=Path, required=True,
                        help="New output folder (must not already exist)")
    parser.add_argument("--arduino-cli", default="arduino-cli",
                        help="Explicit CLI executable/path, default: arduino-cli from PATH")
    parser.add_argument("--timeout", type=int, default=900)
    args = parser.parse_args(argv)
    try:
        destination = build(args.sketch, args.output, args.arduino_cli, args.timeout)
    except (BuildError, OSError, subprocess.SubprocessError) as error:
        print("FAIL: " + str(error), file=sys.stderr)
        return 1
    print("PASS: unsigned LAB firmware package created: " + str(destination))
    print("Hardware was NOT flashed. OTA is NOT authorized. Use supervised USB recovery procedures.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
