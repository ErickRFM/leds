#!/usr/bin/env python3
"""Fail closed when an ESP32 app cannot fit an OTA slot with recovery headroom."""
from pathlib import Path
import argparse
import json
import tempfile
import re

# Arduino-ESP32 3.0.7 4 MB Minimal SPIFFS partition: two 0x1E0000 app slots.
OTA_SLOT_BYTES = 0x1E0000
MIN_HEADROOM_BYTES = 64 * 1024

def evaluate(app: Path) -> dict:
    size = app.stat().st_size
    remaining = OTA_SLOT_BYTES - size
    return {
        "app_bytes": size, "ota_slot_bytes": OTA_SLOT_BYTES,
        "headroom_bytes": remaining, "minimum_headroom_bytes": MIN_HEADROOM_BYTES,
        "fits_with_headroom": remaining >= MIN_HEADROOM_BYTES,
        "ota_upload_enabled": False,
    }

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("build_dir", nargs="?", help="Directory containing esp32_rgb.ino.bin")
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--app-name", default="esp32_rgb.ino.bin",
                        help="Expected Arduino sketch app binary, filename only")
    args = parser.parse_args()
    if args.self_test:
        with tempfile.TemporaryDirectory() as temp:
            dummy = Path(temp) / "esp32_rgb.ino.bin"
            for size, fits in [(1_000_000, True), (OTA_SLOT_BYTES, False),
                               (OTA_SLOT_BYTES - MIN_HEADROOM_BYTES, True),
                               (OTA_SLOT_BYTES - MIN_HEADROOM_BYTES + 1, False)]:
                with dummy.open("wb") as f:
                    f.truncate(size)
                assert evaluate(dummy)["fits_with_headroom"] == fits
        print("PASS: OTA slot size/headroom regression")
        return 0
    if not args.build_dir:
        parser.error("build_dir required unless --self-test")
    if not re.fullmatch(r"[A-Za-z0-9_-]+\\.ino\\.bin", args.app_name):
        parser.error("--app-name must be a safe Arduino app binary filename")
    files = list(Path(args.build_dir).rglob(args.app_name))
    if len(files) != 1:
        parser.error(f"Expected exactly one app binary; found {len(files)}")
    result = evaluate(files[0])
    print(json.dumps(result, indent=2))
    if not result["fits_with_headroom"]:
        print("FAIL: App exceeds safe dual OTA slot budget; shrink firmware or audit partitions.")
        return 1
    print("PASS: app fits the audited 4 MB OTA slot with 64 KiB minimum headroom.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
