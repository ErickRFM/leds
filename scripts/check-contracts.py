#!/usr/bin/env python3
"""Static protocol/circuit audit without requiring Android SDK or Arduino board."""
from pathlib import Path
import re

root = Path(__file__).resolve().parent.parent
firmware = (root / "firmware/esp32_rgb/esp32_rgb.ino").read_text(encoding="utf-8")
android = (root / "android/app/src/main/java/com/rgbcontroller/core/ble/BleManager.kt").read_text(encoding="utf-8")
effects = (root / "android/app/src/main/java/com/rgbcontroller/feature/effects/EffectsScreen.kt").read_text(encoding="utf-8")
protocol = (root / "docs/PROTOCOLO_BLE.md").read_text(encoding="utf-8")

# Keep the visible OLED actions aligned with the authorized HTTP handler
# and the actual SSD1306 renderer. A UI-only face is a broken control.
oled_http = (root / "firmware/esp32_rgb/core/LocalWifiControl.h").read_text(encoding="utf-8")
# The Station route and its reusable sensor/OLED renderer are now separate
# Kotlin components. Audit both together, not only the old monolithic file.
station_dir = root / "android/app/src/main/java/com/rgbcontroller/feature/modules"
station_ui = "\n".join(
    (station_dir / name).read_text(encoding="utf-8")
    for name in ("ModuleWorkspaceScreen.kt", "ModuleReadings.kt")
)
for face in ("happy", "sleepy", "surprised", "pumpkin", "cat", "dance"):
    assert f'"{face}"' in oled_http, f"OLED HTTP route rejects {face}"
    assert f'"{face}"' in firmware, f"OLED firmware cannot render {face}"
    assert f'"{face}"' in station_ui, f"Android station cannot select {face}"
assert 'std::strcmp(value, "dance") == 0' in firmware
assert "drawCatDanceFrame" in firmware

uuid_pattern = r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
uuids = set(re.findall(uuid_pattern, firmware.lower()))
uuids.discard("00002902-0000-1000-8000-00805f9b34fb")
assert len(uuids) == 5, f"Expected RGB V1 plus optional public ID and BLE GPIO-read UUIDs; got {uuids}"
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
ble_gpio_block = firmware.split('gpioStatusCharacteristic = service->createCharacteristic(', 1)[1].split('service->start();', 1)[0]
assert "GPIO_STATUS_UUID, BLECharacteristic::PROPERTY_READ" in ble_gpio_block
assert "PROPERTY_WRITE" not in ble_gpio_block, "GPIO BLE telemetry MUST stay read-only"
assert '"4b9d0005-7a5e-4d4c-ae88-c8c70d3ab211"' in android, "Optional GPIO BLE UUID mismatch"
assert "gpioReadInFlight" in android and "gpioPollingDisabled" in android, "GATT read must not collide with RGB writes"
assert "requestMtu(128)" in android and "BLEDevice::setMTU(128)" in firmware, "MTU negotiation missing"
assert "completeInitialRead" in android and "connectionTimeout" in android, "BLE readiness checks missing"
assert "PROPERTY_READ" in firmware and "PROPERTY_NOTIFY" in firmware
assert "WRITE_TYPE_DEFAULT" in android
assert "onDescriptorWrite" in android and "onCharacteristicRead" in android
pin_manager = (root / "firmware/esp32_rgb/core/PinManager.h").read_text(encoding="utf-8")
registry = (root / "firmware/esp32_rgb/core/ModuleRegistry.h").read_text(encoding="utf-8")
led_module = (root / "firmware/esp32_rgb/modules/LedRgbModule.h").read_text(encoding="utf-8")
assert "moduleRegistry.add(rgbModule)" in firmware, "RGB not registered"
assert "moduleRegistry.startRequiredAndOptional(pinManager, 1)" in firmware, (
    "RGB must be required while optional sensors cannot block SoftAP startup"
)
assert "moduleRegistry.tickAll(millis())" in firmware, "Module ticks not wired"
assert "manager.acquire(pins_[i], id(), PinFunction::PwmOutput)" in led_module, "RGB does not reserve pins"
assert "manager.release(pin, id())" in led_module, "RGB does not release pins"
assert "leasedCount()" in pin_manager, "Missing pin lease state"
assert "startAll" in registry and "stopAll" in registry, "Missing module lifecycle"
lab = (root / "firmware/esp32_rgb/core/LocalWifiControl.h").read_text(encoding="utf-8")
runtime = (root / "firmware/esp32_rgb/core/ExperimentRuntime.h").read_text(encoding="utf-8")
android_lab = (root / "android/app/src/main/java/com/rgbcontroller/core/wifi/LocalLabClient.kt").read_text(encoding="utf-8")
assert 'server_.client().localIP() != WiFi.softAPIP()' in lab, "STA HTTP must be forbidden"
assert '!keyMatches(server_.header("X-Device-Key"), accessKey_)' in lab, "Lab key required"
assert 'mismatch |= static_cast<uint8_t>(presented[i] ^ expected[i]);' in lab, "Lab key must compare all bytes"
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
# Bounded automation must remain AP-authenticated and never be enabled over
# unauthenticated GATT. Parser validation precedes script/automation cancellation.
automation_start = lab.split('server_.on("/v2/automation/start"', 1)[1].split('server_.on("/v2/automation/stop"', 1)[0]
assert 'if (!authorized()) return;' in automation_start
assert 'parseInt("intervalSeconds", 1, 60, seconds)' in automation_start
assert 'parseInt("cycles", 1, 10, cycles)' in automation_start
assert 'script_.active()' in automation_start and 'experiment_.active()' in automation_start
assert 'automation_.configure' in automation_start
assert 'automationEdges_ = static_cast<uint8_t>(cycles * 2)' in automation_start
assert 'automation_.stop()' in manual_http
assert manual_http.index('parseRgbCommand(cmd.c_str(), parsed)') < manual_http.index('automation_.stop()')
assert 'void stopScriptFromBle() { script_.stop(); automation_.stop();' in lab
assert 'automation_.advancedIntervals()' in lab, "Delayed ticks must consume elapsed transitions"
assert 'consumed >= automationEdges_' in lab, "Automation must finish when time budget elapsed"
assert 'rgbRuntime_(String("OFF"))' in lab, "Temporary automation must end safely"
assert 'rgbRuntime_(String(command))' in lab, "Automation must not persist transient changes"
assert 'self->rgbRuntime_(String(command))' in lab, "Script changes must not persist transient state"
assert 'rgbPendingPersist = {' in firmware, "Manual RGB choices need a separate snapshot"
assert 'rgbStorage.save(rgbPendingPersist)' in firmware, "Persist a manual snapshot, not the current temporary effect"
assert 'applyRgbCommandInternal(cmd, false)' in firmware, "Runtime commands must avoid NVS writes"
assert 'applyRgbCommandInternal(cmd, true)' in firmware, "Manual commands must remain persistent"
assert 'rgbRuntime_' in lab, "Keep runtime and manual callbacks separate"
print("PASS V12: automation bounded, authenticated and cancelled only by valid manual RGB commands")

# V36: the environmental screens and the component dashboard must speak exactly
# the frames, states, bands and ids the firmware produces today.
env_codec = (root / "firmware/esp32_rgb/core/BleEnvironmentalCommand.h").read_text(encoding="utf-8")
env_policy = (root / "firmware/esp32_rgb/core/EnvironmentalRgbPolicy.h").read_text(encoding="utf-8")
env_protocol = (root / "android/app/src/main/java/com/rgbcontroller/core/ble/BleEnvironmentalProtocol.kt").read_text(encoding="utf-8")
env_card = (root / "android/app/src/main/java/com/rgbcontroller/feature/led/EnvironmentalRgbControlCard.kt").read_text(encoding="utf-8")

# Every ENV literal the Android protocol relies on has to be a frame the ESP32
# either emits or parses; a reply nobody sends is dead code that fakes a state.
for frame in sorted(set(re.findall(r'"(ENV,[A-Z,]*)"', env_protocol))):
    assert frame in env_codec or frame in firmware, f"Android uses {frame}, which the firmware neither sends nor accepts"

firmware_states = set(re.findall(
    r'return "([A-Z_]+)";', env_policy.split("environmentalRgbStateName", 1)[1].split("}", 1)[0]))
android_states = set(re.split(r'[,\s]+', env_protocol.split("enum class StateEnum", 1)[1]
                              .split("}", 1)[0].strip())) - {""}
assert firmware_states <= android_states, (
    f"Firmware states Android cannot represent: {sorted(firmware_states - android_states)}"
)
assert "UNKNOWN" in android_states, "Android must keep an explicit UNKNOWN state, not a guessed one"
assert "ENABLED" not in android_states, "ENABLED is not a firmware environmental state"

thresholds = [float(value) for value in re.findall(
    r'float (?:coldMaxC|comfortMaxC|warmMaxC|hotMaxC) = ([0-9.]+)f', env_policy)]
assert len(thresholds) == 4, f"Expected four thermal thresholds, found {thresholds}"
cold, comfort, warm, hot = (f"{value:g}" for value in thresholds)
for label in (f"Frío · menos de {cold} °C", f"Confort · {cold} a {comfort} °C",
              f"Cálido · {comfort} a {warm} °C", f"Calor · {warm} a {hot} °C",
              f"Extremo · {hot} °C o más"):
    assert label in env_card, f"Android band legend contradicts the firmware policy: {label}"
for band in re.findall(r'case EnvironmentalRgbBand::([A-Za-z]+):', env_policy):
    assert f'"{band.lower()}"' in env_card, f"Android cannot label the {band} band"

# The protocol document is the contract integrators read; a firmware change
# that leaves it behind reproduces the ENV,ON documentation defect.
assert "ENV,STATE,<ON|OFF>,<ESTADO>" in protocol, "PROTOCOLO_BLE.md lost the environmental reply frame"
for state in sorted(firmware_states):
    assert state in protocol, f"PROTOCOLO_BLE.md no longer documents the firmware state {state}"
assert f"{cold} · {comfort} · {warm} · {hot} °C" in protocol, (
    "PROTOCOLO_BLE.md band thresholds no longer match EnvironmentalRgbPolicy.h"
)

# One Android id table, matching the firmware module ids and the HTTP rewire
# allow-list; a missing entry makes a reassignable component unapplicable.
catalog = (root / "android/app/src/main/java/com/rgbcontroller/core/components/registry/ComponentCatalog.kt").read_text(encoding="utf-8")
firmware_ids = dict(re.findall(
    r'"([a-z_0-9]+)" to "((?:sensor|led|display|audio|robotics)\.[a-z0-9_.]+)"', catalog))
assert firmware_ids, "ComponentCatalog lost its firmware id table"
# Module ids live in modules/*.h (id()) and in the sketch (constructor args),
# so the whole firmware tree is the authority, not only the .ino.
firmware_tree = "\n".join(
    path.read_text(encoding="utf-8")
    for path in sorted((root / "firmware/esp32_rgb").rglob("*"))
    if path.is_file() and path.suffix in (".h", ".cpp", ".ino")
)
for catalog_id, dotted in firmware_ids.items():
    assert f'"{dotted}"' in firmware_tree, f"Firmware has no module id {dotted} for {catalog_id}"
assert "sensor.dht11\" ->" not in catalog, "ComponentCatalog kept a second arrow-mapped id table"
rewire_block = catalog.split("val rewireSignals", 1)[1].split("\n    )", 1)[0]
rewire_counts = dict((key, int(count)) for key, count in
                     re.findall(r'"([a-z_0-9]+)" to (\d)', rewire_block))
firmware_allow_list = set(re.findall(r'componentId != "([a-z_0-9]+)"', lab))
assert set(rewire_counts) == firmware_allow_list, (
    f"Android rewires {sorted(set(rewire_counts) - firmware_allow_list)} that firmware rejects, "
    f"and misses {sorted(firmware_allow_list - set(rewire_counts))}"
)
# Multi-signal components must carry the exact count the firmware demands;
# single-signal ones are padded to the legacy two-slot frame by the client.
for catalog_id, signals in (("robotics_crane", 7), ("led_rgb", 3), ("ssd1306_oled", 2)):
    assert rewire_counts[catalog_id] == signals, f"{catalog_id} signal count drifted"
assert "crane ? 7" in lab and "rgb ? 3" in lab, "Firmware required-signal rule changed; rewireSignals must follow"
assert "ComponentCatalog.rewireSignals[id]" in (root / "android/app/src/main/java/com/rgbcontroller/core/wifi/LocalLabClient.kt").read_text(encoding="utf-8"), (
    "LocalLabClient must read signal counts from ComponentCatalog"
)

print("PASS V36: environmental frames, states, thermal bands and component ids match the firmware")

# V37: the connection hub shows one capability matrix per transport. Every cell
# has to name a frame the ESP32 accepts over GATT or a route the SoftAP serves,
# otherwise the UI is advertising a capability the firmware never exposed.
matrix_src = (root / "android/app/src/main/java/com/rgbcontroller/core/model/TransportCapabilityMatrix.kt").read_text(encoding="utf-8")
# Android builds GATT frames in core/ble (transport) and core/model (codecs), so
# the implementation check spans both; a frame only named in the UI is not sent.
ble_sources = "\n".join(
    path.read_text(encoding="utf-8")
    for path in sorted((root / "android/app/src/main/java/com/rgbcontroller/core").rglob("*.kt"))
)
pin_control = (root / "firmware/esp32_rgb/core/BleSensorPinControl.h").read_text(encoding="utf-8")

def matrix_field(body, name):
    found = re.search(r'\b' + name + r' = "([^"]+)"', body)
    return found.group(1) if found else None

matrix_rows = {}
for chunk in matrix_src.split("CapabilityRow(")[1:]:
    body = chunk.split("\n        )", 1)[0]
    row_id = matrix_field(body, "id")
    if row_id:
        matrix_rows[row_id] = {
            "support": dict(re.findall(r'\b(ble|wifi) = CapabilitySupport\.(\w+)', body)),
            "ble": matrix_field(body, "bleProof"),
            "wifi": matrix_field(body, "wifiProof"),
            "note": matrix_field(body, "note"),
        }

assert len(matrix_rows) >= 15, f"El tablero de capacidades perdió filas: {len(matrix_rows)}"
assert set(matrix_rows) == {"rgb_color", "rgb_effects", "environmental", "sensor_telemetry",
    "component_status", "pin_rewire", "ldr_calibration", "gpio_read", "gpio_write", "adc_raw",
    "crane_motion", "audio", "oled", "automation", "script", "lab_checks", "wifi_config",
    "identity", "ota"}, f"Filas inesperadas en la matriz: {sorted(matrix_rows)}"

for row_id, cells in matrix_rows.items():
    assert set(cells["support"]) == {"ble", "wifi"}, f"{row_id}: falta una columna de transporte"
    for transport, proof in (("ble", cells["ble"]), ("wifi", cells["wifi"])):
        claim = cells["support"][transport]
        if claim == "UNAVAILABLE":
            assert proof is None, f"{row_id}: {transport} bloqueado no puede citar prueba"
            continue
        assert proof, f"{row_id}: {transport} promete {claim} sin marco ni endpoint"
        if transport == "ble":
            assert proof in firmware_tree, f"{row_id}: el firmware no conoce el marco {proof}"
            assert proof in ble_sources, f"{row_id}: Android no implementa {proof}"
        else:
            assert proof in lab, f"{row_id}: el SoftAP del firmware no sirve {proof}"
            assert proof in android_lab, f"{row_id}: LocalLabClient no llama a {proof}"

# Grúa: movimiento solo por Bluetooth; por Wi-Fi únicamente reasigna pines.
assert matrix_rows["crane_motion"]["support"]["wifi"] == "UNAVAILABLE"
assert "/v2/crane" not in lab, "El firmware publicó una ruta HTTP de grúa; la matriz miente"

# El lista de componentes reasignables por Bluetooth es la del firmware, no la de Wi-Fi.
ble_rewire = set(re.findall(r'std::strcmp\(component,"([a-z_0-9]+)"\)', pin_control))
assert ble_rewire == {"robotics_crane", "dht11", "ldr_light"}, f"Whitelist BLE cambió: {sorted(ble_rewire)}"
assert matrix_rows["pin_rewire"]["support"]["ble"] == "LIMITED"
for component_id in ble_rewire:
    assert component_id in (matrix_rows["pin_rewire"]["note"] or ""), (
        f"La matriz no declara que BLE solo reasigna {component_id}")
assert "dht22" not in matrix_rows["pin_rewire"]["note"], "BLE no reasigna dht22; la matriz lo afirma"

# OTA sigue bloqueado en ambos transportes.
assert matrix_rows["ota"]["support"] == {"ble": "UNAVAILABLE", "wifi": "UNAVAILABLE"}
# Identificar el equipo no es autorización.
assert "autoriz" in matrix_rows["identity"]["note"], "La matriz debe separar identidad de autorización"
print("PASS V37: la matriz de capacidades cita marcos GATT y rutas HTTP que el firmware realmente ofrece")

print("PASS: RGB V1 compatibility, core leases, local SoftAP endpoint and lab limits.")
