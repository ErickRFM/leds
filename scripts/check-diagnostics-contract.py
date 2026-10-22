#!/usr/bin/env python3
"""Static smoke-check: diagnostics stay read-only, authenticated and key-free."""
from pathlib import Path
root = Path(__file__).resolve().parent.parent
fw = (root / "firmware/esp32_rgb/core/LocalWifiControl.h").read_text(encoding="utf-8")
android = (root / "android/app/src/main/java/com/rgbcontroller/core/wifi/LocalLabClient.kt").read_text(encoding="utf-8")
assert 'server_.on("/v2/diagnostics", HTTP_GET' in fw
endpoint = fw.split('server_.on("/v2/diagnostics", HTTP_GET', 1)[1].split('server_.on("/v2/firmware"', 1)[0]
assert "if (!authorized()) return;" in endpoint
for banned in ("WiFi.SSID(", "accessKey_", "WiFi.macAddress", 'server_.arg("password")'):
    assert banned not in endpoint, f"Sensitive content in diagnostics: {banned}"
assert 'suspend fun diagnostics(key: String) = request("GET", "/v2/diagnostics", key)' in android
assert 'server_.client().localIP() != WiFi.softAPIP()' in fw
print("PASS: diagnostic endpoint is read-only, key-guarded, AP-only and key-free")
