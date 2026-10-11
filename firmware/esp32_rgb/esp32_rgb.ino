// RGB Controller ESP32 classic - Arduino ESP32 core 3.x
// GPIO R=27 G=25 B=26 (separate 220-330 ohm resistors).
// LAB PROTOTYPE: BLE GATT is not authenticated. Do not expose as a public product.
#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <cstdio>
#include <cstring>
#include "core/PinManager.h"
#include "core/ModuleRegistry.h"
#include "modules/LedRgbModule.h"
#include "core/LocalWifiControl.h"
#include "core/RgbCommandCodec.h"
#include "core/DeviceIdentity.h"
#include "core/RgbSettingsStore.h"

constexpr uint8_t LED_R = 27, LED_G = 25, LED_B = 26;
constexpr bool COMMON_ANODE = true;  // Set false for verified common cathode.
constexpr char DEVICE_NAME[] = "RGB-ESP32";
constexpr char SERVICE_UUID[] = "4b9d0001-7a5e-4d4c-ae88-c8c70d3ab211";
constexpr char WRITE_UUID[] = "4b9d0002-7a5e-4d4c-ae88-c8c70d3ab211";
constexpr char STATE_UUID[] = "4b9d0003-7a5e-4d4c-ae88-c8c70d3ab211";

enum class Mode : uint8_t { NONE, RAINBOW, BREATHE, FADE };
struct RgbState {
  uint8_t r = 0, g = 0, b = 0, brightness = 255;
  bool power = false;
  Mode mode = Mode::NONE;
  uint16_t speed = 100;
};
RgbState rgb;
BLECharacteristic* stateCharacteristic = nullptr;
volatile bool connected = false;
bool connectedPreviously = false;
uint32_t effectStarted = 0;
esp32core::RgbSettingsStore rgbStorage;
bool rgbDirty = false;
bool rgbPersistenceAttempted = false;
uint32_t rgbLastChangedMs = 0;
uint32_t rgbLastPersistAttemptMs = 0;

void persistRgbIfDue(uint32_t now) {
  if (!rgbDirty || static_cast<uint32_t>(now - rgbLastChangedMs) < 2000) return;
  if (rgbPersistenceAttempted &&
      static_cast<uint32_t>(now - rgbLastPersistAttemptMs) < 5000) return;
  rgbPersistenceAttempted = true;
  rgbLastPersistAttemptMs = now;
  const esp32core::RgbSaved saved{
      rgb.r, rgb.g, rgb.b, rgb.brightness, rgb.power ? uint8_t{1} : uint8_t{0},
      static_cast<uint8_t>(rgb.mode), rgb.speed};
  if (rgbStorage.save(saved)) {
    rgbDirty = false;
    rgbPersistenceAttempted = false;
  } else {
    Serial.println("WARN: RGB snapshot could not be persisted; state is temporary");
  }
}


const char* modeName(Mode m) {
  switch (m) {
    case Mode::RAINBOW: return "RAINBOW";
    case Mode::BREATHE: return "BREATHE";
    case Mode::FADE: return "FADE";
    default: return "NONE";
  }
}

void writeLed(uint8_t r, uint8_t g, uint8_t b, uint8_t brightness) {
  uint8_t values[3] = {
    static_cast<uint8_t>((static_cast<uint16_t>(r) * brightness) / 255),
    static_cast<uint8_t>((static_cast<uint16_t>(g) * brightness) / 255),
    static_cast<uint8_t>((static_cast<uint16_t>(b) * brightness) / 255)
  };
  uint8_t pins[3] = {LED_R, LED_G, LED_B};
  for (uint8_t i = 0; i < 3; i++) {
    ledcWrite(pins[i], COMMON_ANODE ? (255 - values[i]) : values[i]);
  }
}

void renderStatic() {
  writeLed(rgb.r, rgb.g, rgb.b, rgb.power ? rgb.brightness : 0);
}

String stateString() {
  return "STATE," + String(rgb.r) + "," + String(rgb.g) + "," +
         String(rgb.b) + "," + String(rgb.brightness) + "," +
         (rgb.power ? "1" : "0") + "," + modeName(rgb.mode) + "," +
         String(rgb.speed);
}

void publishState() {
  if (stateCharacteristic == nullptr) return;
  String frame = stateString();
  stateCharacteristic->setValue(frame.c_str());
  if (connected) stateCharacteristic->notify();
  Serial.println(frame);
}

void reject() {
  Serial.println("ERR,INVALID_COMMAND");
  if (connected && stateCharacteristic != nullptr) {
    stateCharacteristic->setValue("ERR,INVALID_COMMAND");
    stateCharacteristic->notify();
    stateCharacteristic->setValue(stateString().c_str());
  }
}

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer*) override {
    connected = true;
    Serial.println("BLE connected");
  }
  void onDisconnect(BLEServer*) override {
    connected = false;
    Serial.println("BLE disconnected");
  }
};

// One RGB command implementation shared by the legacy BLE service and
// the new AP-only laboratory transport. Invalid input never changes PWM.
bool applyRgbCommand(const String& cmd) {
  String line = cmd;
  line.trim();
  esp32core::RgbCommand command;
  if (!esp32core::parseRgbCommand(line.c_str(), command)) return false;
  if (command.action != esp32core::RgbAction::Get) {
    rgbDirty = true;
    rgbLastChangedMs = millis();
  }
  switch (command.action) {
    case esp32core::RgbAction::Get:
      publishState();
      return true;
    case esp32core::RgbAction::Off:
      rgb.power = false;
      renderStatic();
      publishState();
      return true;
    case esp32core::RgbAction::On:
      rgb.power = true;
      if (rgb.mode == Mode::NONE) renderStatic();
      publishState();
      return true;
    case esp32core::RgbAction::StopEffect:
      rgb.mode = Mode::NONE;
      renderStatic();
      publishState();
      return true;
    case esp32core::RgbAction::Set:
      rgb.r = command.red;
      rgb.g = command.green;
      rgb.b = command.blue;
      rgb.brightness = command.brightness;
      rgb.mode = Mode::NONE;
      rgb.power = true;
      renderStatic();
      publishState();
      return true;
    case esp32core::RgbAction::StartEffect:
      switch (command.effect) {
        case esp32core::RgbEffect::Rainbow: rgb.mode = Mode::RAINBOW; break;
        case esp32core::RgbEffect::Breathe: rgb.mode = Mode::BREATHE; break;
        case esp32core::RgbEffect::Fade: rgb.mode = Mode::FADE; break;
      }
      rgb.speed = command.speed;
      rgb.power = true;
      effectStarted = millis();
      publishState();
      return true;
  }
  return false;
}

void stopScriptOnManualBle();

class CommandCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* characteristic) override {
    const String command = characteristic->getValue();
    if (command != "GET") stopScriptOnManualBle();
    if (!applyRgbCommand(command)) reject();
  }
};

void renderEffect(uint32_t now) {
  if (!rgb.power) {
    writeLed(0, 0, 0, 0);
    return;
  }
  uint32_t elapsed = now - effectStarted;
  if (rgb.mode == Mode::BREATHE) {
    const uint32_t period = static_cast<uint32_t>(rgb.speed) * 20U;
    const uint32_t t = elapsed % period;
    const uint8_t level = static_cast<uint8_t>(t < period / 2
      ? (t * 510U) / period : ((period - t) * 510U) / period);
    const uint8_t adjusted = (static_cast<uint16_t>(rgb.brightness) * level) / 255;
    writeLed(rgb.r, rgb.g, rgb.b, adjusted);
    return;
  }
  uint32_t pos = ((elapsed * 4U) / rgb.speed) % 768U;
  uint8_t r = 0, g = 0, b = 0;
  if (pos < 256U) { r = 255 - pos; g = pos; }
  else if (pos < 512U) { g = 511 - pos; b = pos - 256; }
  else { b = 767 - pos; r = pos - 512; }
  if (rgb.mode == Mode::FADE) {
    // Fade the changing rainbow palette smoothly in and out.
    uint32_t fadePeriod = static_cast<uint32_t>(rgb.speed) * 14U;
    uint32_t phase = elapsed % fadePeriod;
    uint8_t level = static_cast<uint8_t>(phase < fadePeriod / 2
      ? (phase * 510U) / fadePeriod
      : ((fadePeriod - phase) * 510U) / fadePeriod);
    writeLed(r, g, b, static_cast<uint8_t>(
      (static_cast<uint16_t>(level) * rgb.brightness) / 255));
  } else {
    writeLed(r, g, b, rgb.brightness);
  }
}

// Static storage, no heap allocation and no second BLE implementation.
esp32core::DeviceIdentity deviceIdentity;
esp32core::PinManager pinManager;
esp32core::ModuleRegistry moduleRegistry;
esp32core::LedRgbModule rgbModule(
    LED_R, LED_G, LED_B, COMMON_ANODE,
    renderStatic, renderEffect,
    []() { return rgb.mode != Mode::NONE; });
esp32core::LocalWifiControl wifiLab(pinManager, stateString, applyRgbCommand,
    []() { return deviceIdentity.value(); });


void stopScriptOnManualBle() { wifiLab.stopScriptFromBle(); }

void setup() {
  Serial.begin(115200);
  if (!deviceIdentity.begin()) {
    Serial.println("ERROR: V4 identity NVS unavailable; control services disabled");
    while (true) delay(1000);
  }
  esp32core::RgbSaved previous{};
  if (rgbStorage.load(previous)) {
    rgb.r = previous.red;
    rgb.g = previous.green;
    rgb.b = previous.blue;
    rgb.brightness = previous.brightness;
    rgb.power = previous.power == 1;
    rgb.mode = static_cast<Mode>(previous.mode);
    rgb.speed = previous.speed;
    effectStarted = millis();
    Serial.println("RGB configuration restored from versioned NVS snapshot");
  } else {
    Serial.println("RGB NVS unavailable/invalid: booting safely with outputs off");
  }
  if (!moduleRegistry.add(rgbModule) || !moduleRegistry.startAll(pinManager)) {
    Serial.println("ERROR: RGB module failed to reserve/attach PWM");
    while (true) delay(1000);
  }

  BLEDevice::init(DEVICE_NAME);
  // STATE frames can be longer than the 20-byte default notification payload.
  // Android requests 128 as well; peers must negotiate before subscribing.
  BLEDevice::setMTU(128);
  BLEServer* server = BLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());
  BLEService* service = server->createService(SERVICE_UUID);

  BLECharacteristic* cmd = service->createCharacteristic(
      WRITE_UUID, BLECharacteristic::PROPERTY_WRITE);
  cmd->setCallbacks(new CommandCallbacks());

  stateCharacteristic = service->createCharacteristic(
      STATE_UUID, BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY);
  stateCharacteristic->addDescriptor(new BLE2902());
  stateCharacteristic->setValue(stateString().c_str());
  service->start();

  BLEAdvertising* advertising = BLEDevice::getAdvertising();
  advertising->addServiceUUID(SERVICE_UUID);
  advertising->setScanResponse(true);
  BLEDevice::startAdvertising();
  Serial.println("RGB-ESP32 ready (laboratory; BLE not authenticated)");
  wifiLab.begin();
}

void loop() {
  if (!connected && connectedPreviously) {
    delay(120);
    BLEDevice::startAdvertising();
    connectedPreviously = false;
  }
  if (connected) connectedPreviously = true;

  moduleRegistry.tickAll(millis());
  wifiLab.tick(millis());
  persistRgbIfDue(millis());
  delay(5);
}
