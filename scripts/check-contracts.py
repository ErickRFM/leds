#!/usr/bin/env python3
"""Static protocol/circuit audit without requiring Android SDK or Arduino board."""
from pathlib import Path
import re

root = Path(__file__).resolve().parent.parent
firmware = (root / "firmware/esp32_rgb/esp32_rgb.ino").read_text(encoding="utf-8")
android = (root / "android/app/src/main/java/com/rgbcontroller/core/ble/BleManager.kt").read_text(encoding="utf-8")
effects = (root / "android/app/src/main/java/com/rgbcontroller/feature/effects/EffectsScreen.kt").read_text(encoding="utf-8")
protocol = (root / "docs/PROTOCOLO_BLE.md").read_text(encoding="utf-8")

uuid_pattern = r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
uuids = set(re.findall(uuid_pattern, firmware.lower()))
uuids.discard("00002902-0000-1000-8000-00805f9b34fb")
assert len(uuids) == 3, f"Expected 3 unique BLE UUIDs; got {uuids}"
assert uuids <= set(re.findall(uuid_pattern, android.lower())), "UUID mismatch with Android"
assert uuids <= set(re.findall(uuid_pattern, protocol.lower())), "UUID mismatch with protocol docs"

for name, pin in (("LED_R", 27), ("LED_G", 25), ("LED_B", 26)):
    assert re.search(rf"\\b{name}\\s*=\\s*{pin}\\b", firmware), f"Wrong PWM pin: {name}"
for mode in ("RAINBOW", "BREATHE", "FADE"):
    assert f"FX,{mode}," in effects, f"Missing {mode} Android action"
    assert f'"{mode}"' in firmware, f"Missing {mode} ESP32 parser"
assert "FX,STOP" in effects and "FX,STOP" in firmware
assert "PROPERTY_READ" in firmware and "PROPERTY_NOTIFY" in firmware
assert "WRITE_TYPE_DEFAULT" in android
assert "onDescriptorWrite" in android and "onCharacteristicRead" in android
print("PASS: UUIDs, PWM pinout, effects and GATT contract agree.")
