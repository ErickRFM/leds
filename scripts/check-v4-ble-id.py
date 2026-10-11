#!/usr/bin/env python3
"""V4 read-only public identity BLE contract; NOT pairing or authorization."""
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent
fw=(ROOT/"firmware/esp32_rgb/esp32_rgb.ino").read_text()
android=(ROOT/"android/app/src/main/java/com/rgbcontroller/core/ble/BleManager.kt").read_text()
devices=(ROOT/"android/app/src/main/java/com/rgbcontroller/feature/devices/DevicesScreen.kt").read_text()
uuid="4b9d0004-7a5e-4d4c-ae88-c8c70d3ab211"
assert uuid in fw and uuid in android
block=fw.split('createCharacteristic(\n      PUBLIC_ID_UUID,',1)[1].split('service->start()',1)[0]
assert 'BLECharacteristic::PROPERTY_READ' in block
assert 'PROPERTY_WRITE' not in block
assert 'deviceIdentity.value()' in block
assert 'publicIdCharacteristic = service?.getCharacteristic' in android
assert 'DeviceIdCodec.accept' in android
assert '_publicDeviceId.value = null' in android
assert 'finishInitialRead()' in android
assert 'Este ID no concede acceso' in devices
print("PASS V4: public BLE identity readable without secrets or privilege claim")
