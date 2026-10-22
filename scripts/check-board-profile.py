#!/usr/bin/env python3
"""Regression gate: CI, local Windows tool and verified hardware must agree."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent.parent
profile = ROOT / "config" / "esp32-board.env"
values = {}
for line in profile.read_text(encoding="utf-8").splitlines():
    if line and not line.startswith("#"):
        key, sep, value = line.partition("=")
        assert sep and re.fullmatch(r"[A-Z0-9_]+", key), "Malformed board profile"
        assert key not in values and value, "Duplicate or empty board setting"
        values[key] = value

assert set(values) == {"ARDUINO_FQBN", "ARDUINO_ESP32_CORE"}, values
assert values["ARDUINO_FQBN"] == "esp32:esp32:esp32:PartitionScheme=min_spiffs", "4MB dual-OTA profile changed without audit"
assert re.fullmatch(r"\d+\.\d+\.\d+", values["ARDUINO_ESP32_CORE"])
ps = (ROOT / "scripts" / "verify-local.ps1").read_text(encoding="utf-8")
ci = (ROOT / ".github" / "workflows" / "android-ci.yml").read_text(encoding="utf-8")
for text, label in [(ps, "PowerShell"), (ci, "GitHub Actions")]:
    assert "config" in text and "esp32-board.env" in text, f"{label}: profile unused"
    assert "ARDUINO_FQBN" in text and "ARDUINO_ESP32_CORE" in text, f"{label}: keys missing"
assert "if ($trimmed -match '^([A-Z0-9_]+)=(.+)$')" in ps, "PowerShell profile parser must accept digit-containing keys"
assert 'if ($ValidateProfileOnly)' in ps, "Local profile-only validation is required"
assert 'if ($Board -ne $expectedBoard)' in ps, "Manual unsafe partition override must be blocked"
assert 'run: python3 scripts/check-board-profile.py' in ci
print(f"PASS: firmware profile {values['ARDUINO_FQBN']} / Arduino-ESP32 {values['ARDUINO_ESP32_CORE']}")
