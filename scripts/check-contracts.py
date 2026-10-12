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
assert len(uuids) == 4, f"Expected RGB V1 plus read-only public ID UUID; got {uuids}"
assert uuids <= set(re.findall(uuid_pattern, android.lower())), "UUID mismatch with Android"
assert uuids <= set(re.findall(uuid_pattern, protocol.lower())), "UUID mismatch with protocol docs"

for name, pin in (("LED_R", 27), ("LED_G", 25), ("LED_B", 26)):
    assert re.search(rf"\b{name}\s*=\s*{pin}\b", firmware), f"Wrong PWM pin: {name}"
for mode in ("RAINBOW", "BREATHE", "FADE"):
    assert f'EffectItem("{mode}"' in effects, f"Missing {mode} Android action"
    assert f'"{mode}"' in firmware, f"Missing {mode} ESP32 parser"
android_rgb_contract = (root / "android/app/src/main/java/com/rgbcontroller/core/model/RgbDeviceCommand.kt").read_text(encoding="utf-8")
assert "RgbDeviceCommand.effect(effect.id" in effects, "Dynamic FX must use shared typed codec"
assert '"FX," + name + "," + speed' in android_rgb_contract, "Shared effect serialization missing"
codec = (root / "firmware/esp32_rgb/core/RgbCommandCodec.h").read_text(encoding="utf-8")
assert "FX,STOP" in effects and "FX,STOP" in codec
assert "esp32core::parseRgbCommand" in firmware, "Firmware must route RGB through shared codec"
assert "requestMtu(128)" in android and "BLEDevice::setMTU(128)" in firmware, "MTU negotiation missing"
assert "completeInitialRead" in android and "connectionTimeout" in android, "BLE readiness checks missing"
assert "PROPERTY_READ" in firmware and "PROPERTY_NOTIFY" in firmware
assert "WRITE_TYPE_DEFAULT" in android
assert "onDescriptorWrite" in android and "onCharacteristicRead" in android
pin_manager = (root / "firmware/esp32_rgb/core/PinManager.h").read_text(encoding="utf-8")
registry = (root / "firmware/esp32_rgb/core/ModuleRegistry.h").read_text(encoding="utf-8")
led_module = (root / "firmware/esp32_rgb/modules/LedRgbModule.h").read_text(encoding="utf-8")
assert "moduleRegistry.add(rgbModule)" in firmware, "RGB not registered"
assert "moduleRegistry.startAll(pinManager)" in firmware, "Modules not initialized"
assert "moduleRegistry.tickAll(millis())" in firmware, "Module ticks not wired"
assert "manager.acquire(pins_[i], id(), PinFunction::PwmOutput)" in led_module, "RGB does not reserve pins"
assert "manager.release(pin, id())" in led_module, "RGB does not release pins"
assert "leasedCount()" in pin_manager, "Missing pin lease state"
assert "startAll" in registry and "stopAll" in registry, "Missing module lifecycle"
lab = (root / "firmware/esp32_rgb/core/LocalWifiControl.h").read_text(encoding="utf-8")
runtime = (root / "firmware/esp32_rgb/core/ExperimentRuntime.h").read_text(encoding="utf-8")
android_lab = (root / "android/app/src/main/java/com/rgbcontroller/core/wifi/LocalLabClient.kt").read_text(encoding="utf-8")
assert 'server_.client().localIP() != WiFi.softAPIP()' in lab, "STA HTTP must be forbidden"
assert 'server_.header("X-Device-Key") != accessKey_' in lab, "Lab key required"
assert 'manager' not in runtime or 'experiment_' in lab
assert 'pins_.acquire(pin, kManual, PinFunction::DigitalOutput)' in lab, "Pin ownership required"
assert 'digitalWrite(pin, LOW)' in lab, "Safe GPIO reset missing"
assert 'intervalMs < 100' in runtime and 'cycles > 20' in runtime, "Runtime limits missing"
assert 'http://192.168.4.1' in android_lab, "Android must target local SoftAP"
# V8 regression: BLE callbacks must not write PWM or stop scripts on callback
# thread. Only the bounded inbox is consumed by the firmware loop.
callback = firmware.split("class CommandCallbacks", 1)[1].split("void renderEffect", 1)[0]
assert "bleInbox.push(command.c_str())" in callback, "BLE must enqueue for the main loop"
assert "applyRgbCommand(command)" not in callback, "BLE callback must not apply RGB"
assert "stopScriptOnManualBle()" not in callback, "BLE callback must not abort scripts"
assert "portENTER_CRITICAL(&bleInboxMux)" in firmware, "BLE inbox needs task synchronization"
assert "processBleCommands();" in firmware, "Main loop must drain BLE inbox"

manual_http = lab.split('server_.on("/v2/led/command"', 1)[1].split("server_.onNotFound", 1)[0]
assert 'parseRgbCommand(cmd.c_str(), parsed)' in manual_http, "Wi-Fi must validate RGB syntax"
assert manual_http.index("parseRgbCommand(cmd.c_str(), parsed)") < manual_http.index("script_.stop()"), (
    "Malformed Wi-Fi commands must not interrupt a script"
)
assert "if (parsed.action != esp32core::RgbAction::Get)" in manual_http, (
    "GET must never cancel the active script"
)
print("PASS: V8 loop-owned BLE RGB and validation-before-script-interruption")

# V9: a live GPIO panel must read actual firmware leases, never guess them
# from the Android board pin reference. This endpoint stays behind SoftAP auth.
gpio_endpoint = lab.split('server_.on("/v2/gpio/status"', 1)[1].split('server_.on("/v2/gpio/write"', 1)[0]
assert "if (!authorized()) return;" in gpio_endpoint
assert 'pins_.lease(pin)' in gpio_endpoint
assert 'manualLevels_[pin]' in gpio_endpoint
assert '"gpio.manual"' in lab and 'pin_reserved' in lab
assert 'server_.client().localIP() != WiFi.softAPIP()' in lab
studio = (root / "android/app/src/main/java/com/rgbcontroller/feature/programs/ProgramEditorScreen.kt").read_text(encoding="utf-8")
assert 'gpioDraft by rememberSaveable' in studio and 'rgbDraft by rememberSaveable' in studio
assert 'chooseEngine("GPIO")' in studio and 'chooseEngine("RGB")' in studio
client = android_lab
assert '"/v2/gpio/status"' in client
print("PASS V9: runtime GPIO ownership is authenticated; Studio engines keep separate drafts")
print("PASS: RGB V1 compatibility, core leases, local SoftAP endpoint and lab limits.")
