#!/usr/bin/env python3
"""Guard against BLE command paths regressing into silent GATT stalls."""
from pathlib import Path
root = Path(__file__).resolve().parent.parent
manager = (root / "android/app/src/main/java/com/rgbcontroller/core/ble/BleManager.kt").read_text(encoding="utf-8")
assert "const val WRITE_TIMEOUT_MS = 5000L" in manager
assert "handler.postDelayed(writeTimeout, WRITE_TIMEOUT_MS)" in manager
assert "handler.removeCallbacks(writeTimeout)" in manager
assert 'lostLink("ESP32 no confirmó escritura BLE en 5 s")' in manager
assert "remote !== gatt || characteristic.uuid != COMMAND_CHAR_UUID" in manager
assert "RgbDeviceCommand.allowed(text)" in manager
assert "lostLink(\"No se pudo iniciar escritura GATT\")" in manager
assert "closeConnection()" in manager
print("PASS: BLE command guards, fail-closed write timeout, and stale-GATT callback rejection")
