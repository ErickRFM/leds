// RGB Controller ESP32 classic - Arduino ESP32 core 3.x
// GPIO R=27 G=25 B=26 (separate 220-330 ohm resistors).
// LAB PROTOTYPE: BLE GATT is protected via fail-closed channel authorization.
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
#include "core/RgbCommandInbox.h"
#include "core/DeviceIdentity.h"
#include "core/RgbSettingsStore.h"
#include "core/PhysicalProvisioningMode.h"
#include "core/CompiCommandArbiter.h"
#include "core/ComponentContract.h"
#include "core/ComponentCatalog.h"
#include "core/SensorTelemetry.h"
#include "core/SensorTelemetryCodec.h"
#include "modules/Dht22Module.h"
#include "modules/SoilMoistureModule.h"
#include "modules/OledSsd1306Module.h"

// ---------------------------------------------------------------------------
// Optional modular hardware components.
//
// Everything below defaults to OFF because the lab board may not have any
// sensor wired. A component is only registered when explicitly enabled at
// compile time AND its driver is available; the firmware never pretends a
// component exists just because the Android catalog lists it.
// ---------------------------------------------------------------------------
#ifndef COMPI_ENABLE_DHT22
#define COMPI_ENABLE_DHT22 0
#endif
#ifndef COMPI_DHT22_PIN
#define COMPI_DHT22_PIN 32
#endif
#ifndef COMPI_ENABLE_SOIL
#define COMPI_ENABLE_SOIL 0
#endif
#ifndef COMPI_SOIL_PIN
#define COMPI_SOIL_PIN 34
#endif
#ifndef COMPI_ENABLE_OLED
#define COMPI_ENABLE_OLED 0
#endif
#ifndef COMPI_OLED_SDA
#define COMPI_OLED_SDA 21
#endif
#ifndef COMPI_OLED_SCL
#define COMPI_OLED_SCL 22
#endif
#ifndef COMPI_OLED_ADDRESS
#define COMPI_OLED_ADDRESS 0x3C
#endif

constexpr uint8_t LED_R = 27, LED_G = 25, LED_B = 26;
constexpr bool COMMON_ANODE = true;  // Set false for verified common cathode.
constexpr char DEVICE_NAME[] = "RGB-ESP32";
constexpr char SERVICE_UUID[] = "4b9d0001-7a5e-4d4c-ae88-c8c70d3ab211";
constexpr char WRITE_UUID[] = "4b9d0002-7a5e-4d4c-ae88-c8c70d3ab211";
constexpr char STATE_UUID[] = "4b9d0003-7a5e-4d4c-ae88-c8c70d3ab211";
constexpr char PUBLIC_ID_UUID[] = "4b9d0004-7a5e-4d4c-ae88-c8c70d3ab211";
constexpr char GPIO_STATUS_UUID[] = "4b9d0005-7a5e-4d4c-ae88-c8c70d3ab211";

enum class Mode : uint8_t { NONE, RAINBOW, BREATHE, FADE };
struct RgbState {
  uint8_t r = 0, g = 0, b = 0, brightness = 255;
  bool power = false;
  Mode mode = Mode::NONE;
  uint16_t speed = 100;
};
RgbState rgb;
BLECharacteristic* stateCharacteristic = nullptr;
BLECharacteristic* gpioStatusCharacteristic = nullptr;
uint32_t lastGpioBleUpdateMs = 0;
volatile bool connected = false;
volatile esp32core::BleAuthState bleAuthState = esp32core::BleAuthState::DISCONNECTED;
bool connectedPreviously = false;
uint32_t physicalProvisionHoldStarted = 0;
uint32_t effectStarted = 0;

#if COMPI_ENABLE_DHT22
// Bit-banged AM2302/DHT22 read. Returns ok=false on timeout or checksum error;
// callers must treat that as "no data", never as a valid 0% reading.
esp32core::Dht22Module::Reading dht22Read(void*) {
  esp32core::Dht22Module::Reading result{false, 0.0f, 0.0f};
  uint8_t data[5] = {0, 0, 0, 0, 0};
  pinMode(COMPI_DHT22_PIN, OUTPUT);
  digitalWrite(COMPI_DHT22_PIN, LOW);
  delay(2);
  digitalWrite(COMPI_DHT22_PIN, HIGH);
  delayMicroseconds(40);
  pinMode(COMPI_DHT22_PIN, INPUT_PULLUP);
  if (pulseIn(COMPI_DHT22_PIN, LOW, 1000) == 0) return result;
  if (pulseIn(COMPI_DHT22_PIN, HIGH, 1000) == 0) return result;
  for (int i = 0; i < 40; ++i) {
    const unsigned long highDuration = pulseIn(COMPI_DHT22_PIN, HIGH, 1000);
    if (highDuration == 0) return result;
    data[i / 8] <<= 1;
    if (highDuration > 40) data[i / 8] |= 1;
  }
  const uint8_t checksum = static_cast<uint8_t>(data[0] + data[1] + data[2] + data[3]);
  if (checksum != data[4]) return result;
  const uint16_t rawHumidity = (static_cast<uint16_t>(data[0]) << 8) | data[1];
  int16_t rawTemperature = ((static_cast<uint16_t>(data[2] & 0x7F) << 8) | data[3]);
  if (data[2] & 0x80) rawTemperature = -rawTemperature;
  result.humidityPercent = static_cast<float>(rawHumidity) / 10.0f;
  result.temperatureC = static_cast<float>(rawTemperature) / 10.0f;
  result.ok = true;
  return result;
}
#endif  // COMPI_ENABLE_DHT22

#if COMPI_ENABLE_SOIL
int soilAdcRead(uint8_t pin, void*) { return analogRead(pin); }
#endif  // COMPI_ENABLE_SOIL

#if COMPI_ENABLE_OLED
#include <Wire.h>
#if __has_include(<Adafruit_SSD1306.h>)
#include <Adafruit_SSD1306.h>
#define COMPI_OLED_LIBRARY 1
Adafruit_SSD1306 oledDriver(128, 64, &Wire, -1);
bool oledInit(uint8_t address, uint8_t sda, uint8_t scl, void*) {
  Wire.begin(sda, scl);
  if (!oledDriver.begin(SSD1306_SWITCHCAPVCC, address)) return false;
  oledDriver.clearDisplay();
  oledDriver.display();
  return true;
}
bool oledPresent(uint8_t, const char* text, void*) {
  oledDriver.clearDisplay();
  oledDriver.setTextSize(1);
  oledDriver.setCursor(0, 0);
  oledDriver.println(text ? text : "");
  oledDriver.display();
  return true;
}
#else
// No display library available: the module is intentionally left unsupported
// rather than claiming pixels were drawn.
#define COMPI_OLED_LIBRARY 0
#endif
#endif  // COMPI_ENABLE_OLED

esp32core::RgbSettingsStore rgbStorage;
bool rgbDirty = false;
// Only explicit manual RGB choices become persistent. Temporary scripts and
// automation transitions must never overwrite the user's restored preference.
esp32core::RgbSaved rgbPendingPersist{};
bool rgbPersistenceAttempted = false;
// Legacy BLE stack invokes onWrite on a different task. Never mutate the
// RGB engine/LED state in that callback; the Arduino loop owns all commands.
portMUX_TYPE bleInboxMux = portMUX_INITIALIZER_UNLOCKED;
esp32core::RgbCommandInbox bleInbox;
uint16_t bleRejected = 0;
uint32_t rgbLastChangedMs = 0;
uint32_t rgbLastPersistAttemptMs = 0;
esp32core::CompiCommandArbiter commandArbiter;

void persistRgbIfDue(uint32_t now) {
  if (!rgbDirty || static_cast<uint32_t>(now - rgbLastChangedMs) < 2000) return;
  if (rgbPersistenceAttempted &&
      static_cast<uint32_t>(now - rgbLastPersistAttemptMs) < 5000) return;
  rgbPersistenceAttempted = true;
  rgbLastPersistAttemptMs = now;
  if (rgbStorage.save(rgbPendingPersist)) {
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
    bleAuthState = esp32core::BleAuthState::CONNECTED_UNAUTHENTICATED;
    Serial.println("BLE connected (unauthenticated)");
  }
  void onDisconnect(BLEServer* server) override {
    connected = false;
    bleAuthState = esp32core::BleAuthState::DISCONNECTED;
    Serial.println("BLE disconnected; restarting advertising");
    if (server) {
      BLEDevice::startAdvertising();
    }
  }
};

// One RGB command implementation shared by BLE, SoftAP and bounded runtimes.
// Only explicit/manual operations persist a copy of the chosen configuration.
// The loop is the sole RGB state owner; callbacks enqueue instead of mutating.
bool applyRgbCommandInternal(const String& cmd, bool persistManualChoice) {
  String line = cmd;
  line.trim();
  esp32core::RgbCommand command{};
  if (!esp32core::parseRgbCommand(line.c_str(), command)) return false;
  switch (command.action) {
    case esp32core::RgbAction::Get:
      publishState();
      return true;
    case esp32core::RgbAction::Off:
      rgb.power = false;
      renderStatic();
      break;
    case esp32core::RgbAction::On:
      rgb.power = true;
      if (rgb.mode == Mode::NONE) renderStatic();
      break;
    case esp32core::RgbAction::StopEffect:
      rgb.mode = Mode::NONE;
      renderStatic();
      break;
    case esp32core::RgbAction::Set:
      rgb.r = command.red;
      rgb.g = command.green;
      rgb.b = command.blue;
      rgb.brightness = command.brightness;
      rgb.mode = Mode::NONE;
      rgb.power = true;
      renderStatic();
      break;
    case esp32core::RgbAction::StartEffect:
      switch (command.effect) {
        case esp32core::RgbEffect::Rainbow: rgb.mode = Mode::RAINBOW; break;
        case esp32core::RgbEffect::Breathe: rgb.mode = Mode::BREATHE; break;
        case esp32core::RgbEffect::Fade: rgb.mode = Mode::FADE; break;
      }
      rgb.speed = command.speed;
      rgb.power = true;
      effectStarted = millis();
      break;
    default:
      return false;
  }
  if (persistManualChoice) {
    rgbPendingPersist = {rgb.r, rgb.g, rgb.b, rgb.brightness, rgb.power};
    rgbDirty = true;
    rgbLastChangedMs = millis();
    rgbPersistenceAttempted = false;
  }
  publishState();
  return true;
}

bool applyRgbCommand(const String& cmd) {
  return applyRgbCommandInternal(cmd, true);
}

bool applyRgbRuntimeCommand(const String& cmd) {
  return applyRgbCommandInternal(cmd, false);
}

void stopScriptOnManualBle();

class CommandCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* characteristic) override {
    const String command = characteristic->getValue();
    esp32core::RgbCommand parsed{};
    const bool valid = esp32core::parseRgbCommand(command.c_str(), parsed);
    portENTER_CRITICAL(&bleInboxMux);
    const bool accepted = valid && bleInbox.push(command.c_str());
    if (!accepted && bleRejected < 65535) ++bleRejected;
    portEXIT_CRITICAL(&bleInboxMux);
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

#if COMPI_ENABLE_DHT22
esp32core::Dht22Module dht22Module(COMPI_DHT22_PIN, dht22Read, nullptr, 2000);
#endif
#if COMPI_ENABLE_SOIL
esp32core::SoilMoistureModule soilModule(COMPI_SOIL_PIN, soilAdcRead, nullptr, 1000);
#endif
#if COMPI_ENABLE_OLED && COMPI_OLED_LIBRARY
esp32core::OledSsd1306Module oledModule(COMPI_OLED_ADDRESS, COMPI_OLED_SDA,
    COMPI_OLED_SCL, oledInit, oledPresent, nullptr, 250);
#endif

// Collects the freshest measurement from every active component. Returns false
// only when the codec cannot fit the payload; an empty (but valid) list is a
// legitimate response while no component is active.
bool collectComponentTelemetry(char* output, size_t capacity, void*) {
  esp32core::Measurement entries[8];
  size_t count = 0;
#if COMPI_ENABLE_DHT22
  if (dht22Module.ready()) {
    entries[count++] = dht22Module.humidityReading();
    entries[count++] = dht22Module.temperatureReading();
  }
#endif
#if COMPI_ENABLE_SOIL
  if (soilModule.ready()) {
    entries[count++] = soilModule.rawReading();
    entries[count++] = soilModule.moistureReading();
  }
#endif
  return esp32core::SensorTelemetryCodec::encode(entries, count, output, capacity);
}

esp32core::LocalWifiControl wifiLab(pinManager, stateString, applyRgbCommand,
    applyRgbRuntimeCommand, []() { return deviceIdentity.value(); });

void stopScriptOnManualBle() { wifiLab.stopScriptFromBle(); }

void processBleCommands() {
  uint16_t rejected = 0;
  portENTER_CRITICAL(&bleInboxMux);
  rejected = bleRejected;
  bleRejected = 0;
  portEXIT_CRITICAL(&bleInboxMux);
  if (rejected) reject();
  for (uint8_t i = 0; i < 4; ++i) {
    char buffer[esp32core::RgbCommandInbox::kMaxCommand + 1] = {};
    portENTER_CRITICAL(&bleInboxMux);
    const bool found = bleInbox.pop(buffer);
    portEXIT_CRITICAL(&bleInboxMux);
    if (!found) break;
    esp32core::RgbCommand parsed{};
    if (!esp32core::parseRgbCommand(buffer, parsed)) {
      reject();
      continue;
    }
    const bool isStop = (parsed.action == esp32core::RgbAction::StopEffect || parsed.action == esp32core::RgbAction::Off);
    const auto channelSource = esp32core::CompiCommandArbiter::mapBleSource(bleAuthState);
    auto evaluation = commandArbiter.evaluate(channelSource, isStop);
    if (!evaluation.allowed) {
      reject();
      continue;
    }
    if (parsed.action != esp32core::RgbAction::Get) {
      stopScriptOnManualBle();
    }
    if (!applyRgbCommand(buffer)) {
      reject();
    }
  }
}

void setup() {
  Serial.begin(115200);
  Serial.println("\n--- COMPI ESP32 LAB V15 ---");

  deviceIdentity.begin();

  pinManager.registerSystemPin(LED_R, "led.rgb");
  pinManager.registerSystemPin(LED_G, "led.rgb");
  pinManager.registerSystemPin(LED_B, "led.rgb");

  esp32core::RgbSaved saved{};
  if (rgbStorage.load(saved)) {
    rgb.r = saved.red;
    rgb.g = saved.green;
    rgb.b = saved.blue;
    rgb.brightness = saved.brightness;
    rgb.power = saved.power != 0;
    Serial.println("Restored explicit RGB user choice from NVS");
  } else {
    rgb.r = 255; rgb.g = 255; rgb.b = 255; rgb.brightness = 255; rgb.power = true;
  }

  moduleRegistry.registerModule(&rgbModule);
#if COMPI_ENABLE_DHT22
  moduleRegistry.registerModule(&dht22Module);
#endif
#if COMPI_ENABLE_SOIL
  moduleRegistry.registerModule(&soilModule);
#endif
#if COMPI_ENABLE_OLED && COMPI_OLED_LIBRARY
  moduleRegistry.registerModule(&oledModule);
#endif
  moduleRegistry.startAll(pinManager);

  wifiLab.setComponentTelemetry(collectComponentTelemetry, nullptr);
  wifiLab.begin();

  BLEDevice::init(DEVICE_NAME);
  BLEServer* server = BLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());
  BLEService* service = server->createService(SERVICE_UUID);

  BLECharacteristic* writeChar = service->createCharacteristic(
      WRITE_UUID, BLECharacteristic::PROPERTY_WRITE);
  writeChar->setCallbacks(new CommandCallbacks());

  stateCharacteristic = service->createCharacteristic(
      STATE_UUID,
      BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY);
  stateCharacteristic->addDescriptor(new BLE2902());

  BLECharacteristic* publicIdChar = service->createCharacteristic(
      PUBLIC_ID_UUID, BLECharacteristic::PROPERTY_READ);
  const String publicId = deviceIdentity.value();
  publicIdChar->setValue(publicId.c_str());

  gpioStatusCharacteristic = service->createCharacteristic(
      GPIO_STATUS_UUID,
      BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY);
  gpioStatusCharacteristic->addDescriptor(new BLE2902());

  service->start();
  BLEAdvertising* advertising = BLEDevice::getAdvertising();
  advertising->addServiceUUID(SERVICE_UUID);
  advertising->setScanResponse(true);
  advertising->setMinPreferred(0x06);
  advertising->setMinPreferred(0x12);
  BLEDevice::startAdvertising();

  publishState();
  Serial.println("BLE initialized & advertising");
}

void loop() {
  uint32_t now = millis();
  processBleCommands();
  wifiLab.tick(now);
  moduleRegistry.tickAll(now);
  persistRgbIfDue(now);

  if (now - lastGpioBleUpdateMs >= 1000) {
    lastGpioBleUpdateMs = now;
    if (connected && gpioStatusCharacteristic != nullptr) {
      char payload[96] = {};
      if (wifiLab.writeBlePinSnapshot(payload)) {
        gpioStatusCharacteristic->setValue(payload);
        gpioStatusCharacteristic->notify();
      }
    }
  }

  if (connected && !connectedPreviously) {
    connectedPreviously = true;
    publishState();
  } else if (!connected && connectedPreviously) {
    connectedPreviously = false;
  }
}
