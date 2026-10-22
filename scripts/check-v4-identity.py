#!/usr/bin/env python3
"""V4 security regression: identity must never masquerade as ownership."""
from pathlib import Path
ROOT = Path(__file__).resolve().parent.parent
wifi = (ROOT / "firmware/esp32_rgb/core/LocalWifiControl.h").read_text()
fw = (ROOT / "firmware/esp32_rgb/esp32_rgb.ino").read_text()
identity = (ROOT / "firmware/esp32_rgb/core/DeviceIdentity.h").read_text()
assert 'server_.on("/v2/identity", HTTP_GET' in wifi
identity_route = wifi.split('server_.on("/v2/identity", HTTP_GET',1)[1].split('server_.on("/v2/firmware"',1)[0]
assert "if (!authorized()) return;" in identity_route
assert '"secureProvisioning\\":false' in identity_route
assert '"legacyBleWriteUnauthenticated\\":true' in identity_route
assert '"ownerBound\\":false' in identity_route
assert 'prefs.begin("cc-v4-id", false)' in identity
assert "prefs.getString" in identity and "prefs.putString" in identity
assert "deviceIdentity.begin()" in fw and "while (true) delay(1000);" in fw
assert "PROPERTY_WRITE" in fw # Existing GATT is NOT yet authorized.
assert "DeviceIdentity deviceIdentity" in fw
print("PASS V4: public stable device ID, restricted readback, no false secure pairing claim")
