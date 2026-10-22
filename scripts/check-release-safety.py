#!/usr/bin/env python3
"""Prevent regressions that accidentally expose an insecure production build."""
from pathlib import Path

root = Path(__file__).resolve().parent.parent
gradle = (root / "android/app/build.gradle.kts").read_text(encoding="utf-8")
debug = (root / "android/app/src/debug/AndroidManifest.xml").read_text(encoding="utf-8")
main = (root / "android/app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
assert 'beforeVariants(selector().withBuildType("release"))' in gradle
assert 'builder.enable = false' in gradle, "Release cannot be built before hardware security gates"
assert 'android:usesCleartextTraffic="true"' in debug
assert 'android:usesCleartextTraffic="true"' not in main
print("PASS: unsecured ESP32 laboratory controls remain debug-only")
