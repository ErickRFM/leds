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
#include "core/BleSecurityPolicy.h"
#include "core/OwnerEnrollment.h"
#include "core/OwnerEnrollmentFrames.h"
#include "core/OwnerEnrollmentSession.h"
#include "core/NvsOwnerEnrollmentStore.h"
#include "core/MbedTlsSessionCrypto.h"
#include "core/CommandAuthorization.h"
#include "core/ComponentContract.h"
#include "core/ComponentCatalog.h"
#include "core/ComponentStatus.h"
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
// Drivers may be built without taking pins. COMPI_ENABLE_* continues to
// mean "start at boot" for backward compatibility with opt-in firmware.
#ifndef COMPI_BUILD_DHT22
#define COMPI_BUILD_DHT22 1
#endif
#ifndef COMPI_BUILD_SOIL
#define COMPI_BUILD_SOIL 1
#endif
#ifndef COMPI_BUILD_OLED
#define COMPI_BUILD_OLED 1
#endif
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
volatile bool restartBleAdvertising = false;
volatile uint32_t bleDisconnectMs = 0;
// Single source of truth for BLE link security and owner authorization.
esp32core::BleSecurityPolicy bleSecurity;
// SEG.1b-2b: the owner-enrollment session is the authority for privileged BLE
// commands. It composes framing, the enrollment state machine, real mbedTLS
// crypto and NVS persistence. No new UUIDs: it rides the existing WRITE
// characteristic. The link state (bleSecurity) only feeds logging/bonding.
esp32core::MbedTlsSessionCrypto ownerCrypto;
esp32core::NvsOwnerEnrollmentStore ownerStore;
esp32core::OwnerEnrollmentSession ownerSession(ownerCrypto, ownerStore);
// BLE task callbacks transition the policy; the main loop reads the derived
// state. Kept volatile so the loop observes callback updates without a lock.
volatile esp32core::BleAuthState bleAuthState = esp32core::BleAuthState::DISCONNECTED;
// Ownership is authoritative: only a live owner session grants AUTHORIZED. The
// encrypted/bonded link state alone never does.
void syncBleAuthState() {
  if (!connected) {
    bleAuthState = esp32core::BleAuthState::DISCONNECTED;
  } else {
    bleAuthState = ownerSession.authenticated()
        ? esp32core::BleAuthState::AUTHORIZED
        : esp32core::BleAuthState::CONNECTED_UNAUTHENTICATED;
  }
}
bool connectedPreviously = false;
uint32_t physicalProvisionHoldStarted = 0;
uint32_t effectStarted = 0;

#if COMPI_BUILD_DHT22
uint8_t liveDhtPin = COMPI_DHT22_PIN;
// Bit-banged AM2302/DHT22 read. Returns ok=false on timeout or checksum error;
// callers must treat that as "no data", never as a valid 0% reading.
esp32core::Dht22Module::Reading dht22Read(void*) {
  esp32core::Dht22Module::Reading result{false, 0.0f, 0.0f};
  uint8_t data[5] = {0, 0, 0, 0, 0};
  pinMode(liveDhtPin, OUTPUT);
  digitalWrite(liveDhtPin, LOW);
  delay(2);
  digitalWrite(liveDhtPin, HIGH);
  delayMicroseconds(40);
  pinMode(liveDhtPin, INPUT_PULLUP);
  if (pulseIn(liveDhtPin, LOW, 1000) == 0) return result;
  if (pulseIn(liveDhtPin, HIGH, 1000) == 0) return result;
  for (int i = 0; i < 40; ++i) {
    const unsigned long highDuration = pulseIn(liveDhtPin, HIGH, 1000);
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

#if COMPI_BUILD_SOIL
int soilAdcRead(uint8_t pin, void*) { return analogRead(pin); }
#endif  // COMPI_ENABLE_SOIL

#if COMPI_BUILD_OLED
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
// Owner-enrollment frames are longer than RGB commands (a PAIR/AUTH line can
// exceed 100 chars), so they get their own small fixed inbox rather than
// growing the RGB command buffer. Same pattern: enqueue in the BLE callback,
// drain in the main loop under a portMUX.
constexpr size_t kEnrollLineMax = 128;
struct EnrollmentInbox {
  static constexpr size_t kCapacity = 4;
  bool push(const char* line) {
    if (!line || count == kCapacity) return false;
    const size_t length = strlen(line);
    if (length == 0 || length > kEnrollLineMax) return false;
    std::memcpy(lines[tail], line, length);
    lines[tail][length] = '\0';
    tail = (tail + 1) % kCapacity;
    ++count;
    return true;
  }
  bool pop(char (&out)[kEnrollLineMax + 1]) {
    if (count == 0) return false;
    std::memcpy(out, lines[head], kEnrollLineMax + 1);
    head = (head + 1) % kCapacity;
    --count;
    return true;
  }
  char lines[kCapacity][kEnrollLineMax + 1] = {};
  size_t head = 0, tail = 0, count = 0;
};
portMUX_TYPE bleEnrollMux = portMUX_INITIALIZER_UNLOCKED;
EnrollmentInbox enrollInbox;
uint16_t bleEnrollRejected = 0;
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

// Transient control reply (owner enrollment / errors) over the existing STATE
// characteristic. Never a new UUID; restored to the live RGB snapshot after.
void publishControlReply(const char* frame) {
  Serial.println(frame);
  if (connected && stateCharacteristic != nullptr) {
    stateCharacteristic->setValue(frame);
    stateCharacteristic->notify();
    stateCharacteristic->setValue(stateString().c_str());
  }
}

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer*) override {
    connected = true;
    // Connecting never authenticates: the session starts fail-closed.
    bleSecurity.onConnected();
    syncBleAuthState();
    Serial.println("BLE connected (unauthenticated)");
  }
  void onDisconnect(BLEServer* server) override {
    connected = false;
    // Disconnect revokes authorization unconditionally.
    bleSecurity.onDisconnected();
    // The owner session drops too; ownership itself survives for recovery.
    ownerSession.onDisconnect();
    syncBleAuthState();
    // Restart asynchronously in loop to avoid a GAP stop/start collision.
    bleDisconnectMs = millis();
    restartBleAdvertising = true;
    Serial.println("BLE disconnected; advertising restart scheduled");
  }
};

// Real GAP security events. Bonding/encryption make an encrypted link available
// but never authorize; the owner key must still be presented over that link.
class BleLinkSecurityCallbacks : public BLESecurityCallbacks {
  uint32_t onPassKeyRequest() override { return 0; }
  void onPassKeyNotify(uint32_t) override {}
  bool onSecurityRequest() override { return true; }
  bool onConfirmPIN(uint32_t) override { return true; }
  void onAuthenticationComplete(esp_ble_auth_cmpl_t param) override {
    if (param.success) {
      bleSecurity.onLinkSecured(param.key_present);
      Serial.println(param.key_present ? "BLE link encrypted & bonded"
                                       : "BLE link encrypted");
    } else {
      bleSecurity.onPairingFailed();
      Serial.println("BLE pairing failed; channel stays unauthenticated");
    }
    syncBleAuthState();
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

// True when the line's first token is a client owner-enrollment verb. Full
// validation happens on the main loop via OwnerEnrollmentFrames; here we only
// route the write to the (larger) enrollment inbox.
bool looksLikeEnrollment(const char* line) {
  static const char* verbs[] = {"PAIR", "BEGIN", "AUTH", "REVOKE", "RECOVER", "RESET"};
  if (!line) return false;
  for (const char* verb : verbs) {
    const size_t length = std::strlen(verb);
    if (std::strncmp(line, verb, length) == 0 &&
        (line[length] == ' ' || line[length] == '\0')) {
      return true;
    }
  }
  return false;
}

class CommandCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* characteristic) override {
    const String command = characteristic->getValue();
    if (looksLikeEnrollment(command.c_str())) {
      portENTER_CRITICAL(&bleEnrollMux);
      const bool accepted = enrollInbox.push(command.c_str());
      if (!accepted && bleEnrollRejected < 65535) ++bleEnrollRejected;
      portEXIT_CRITICAL(&bleEnrollMux);
      return;
    }
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
  // Promote before multiplying to prevent overflow after long device uptime.
  const uint32_t pos = static_cast<uint32_t>(
      ((static_cast<uint64_t>(elapsed) * 4ULL) / rgb.speed) % 768ULL);
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

#if COMPI_BUILD_DHT22
esp32core::Dht22Module dht22Module(COMPI_DHT22_PIN, dht22Read, nullptr, 2000);
#endif
#if COMPI_BUILD_SOIL
esp32core::SoilMoistureModule soilModule(COMPI_SOIL_PIN, soilAdcRead, nullptr, 1000);
#endif
#if COMPI_BUILD_OLED && COMPI_OLED_LIBRARY
esp32core::OledSsd1306Module oledModule(COMPI_OLED_ADDRESS, COMPI_OLED_SDA,
    COMPI_OLED_SCL, oledInit, oledPresent, nullptr, 250);
#endif

// Collects the freshest measurement from every active component. Returns false
// only when the codec cannot fit the payload; an empty (but valid) list is a
// legitimate response while no component is active.
bool collectComponentTelemetry(char* output, size_t capacity, void*) {
  esp32core::Measurement entries[8];
  size_t count = 0;
#if COMPI_BUILD_DHT22
  if (dht22Module.ready()) {
    entries[count++] = dht22Module.humidityReading();
    entries[count++] = dht22Module.temperatureReading();
  }
#endif
#if COMPI_BUILD_SOIL
  if (soilModule.ready()) {
    entries[count++] = soilModule.rawReading();
    entries[count++] = soilModule.moistureReading();
  }
#endif
  return esp32core::SensorTelemetryCodec::encode(entries, count, output, capacity);
}

// Authoritative runtime component status. The firmware owns every pin; this
// report exposes the exact pins currently leased by each started module and its
// lifecycle, so clients never infer GPIO ownership from their local config.
bool collectComponentStatus(char* output, size_t capacity, void*) {
  esp32core::ComponentStatusEntry entries[8];
  size_t count = 0;

  {
    esp32core::ComponentStatusEntry& entry = entries[count++];
    entry = {};
    entry.id = rgbModule.id();
    entry.type = esp32core::ComponentType::Actuator;
    entry.interfaceType = esp32core::HardwareInterface::Pwm;
    entry.pins[0] = rgbModule.redPin();
    entry.pins[1] = rgbModule.greenPin();
    entry.pins[2] = rgbModule.bluePin();
    entry.pinCount = 3;
    entry.active = rgbModule.ready();
    entry.state = entry.active ? esp32core::ComponentState::Active
                               : esp32core::ComponentState::Error;
  }

#if COMPI_BUILD_DHT22
  {
    esp32core::ComponentStatusEntry& entry = entries[count++];
    entry = {};
    entry.id = dht22Module.id();
    entry.type = esp32core::ComponentType::Sensor;
    entry.interfaceType = esp32core::HardwareInterface::OneWire;
    entry.pins[0] = dht22Module.pin();
    entry.pinCount = 1;
    entry.active = dht22Module.ready();
    entry.state = entry.active ? esp32core::ComponentState::Active
                               : esp32core::ComponentState::Catalog;
  }
#endif
#if COMPI_BUILD_SOIL
  {
    esp32core::ComponentStatusEntry& entry = entries[count++];
    entry = {};
    entry.id = soilModule.id();
    entry.type = esp32core::ComponentType::Sensor;
    entry.interfaceType = esp32core::HardwareInterface::Adc;
    entry.pins[0] = soilModule.pin();
    entry.pinCount = 1;
    entry.active = soilModule.ready();
    entry.state = entry.active ? esp32core::ComponentState::Active
                               : esp32core::ComponentState::Catalog;
  }
#endif
#if COMPI_BUILD_OLED && COMPI_OLED_LIBRARY
  {
    esp32core::ComponentStatusEntry& entry = entries[count++];
    entry = {};
    entry.id = oledModule.id();
    entry.type = esp32core::ComponentType::Display;
    entry.interfaceType = esp32core::HardwareInterface::I2c;
    entry.pins[0] = oledModule.sda();
    entry.pins[1] = oledModule.scl();
    entry.pinCount = 2;
    entry.active = oledModule.ready();
    entry.state = entry.active ? esp32core::ComponentState::Active
                               : esp32core::ComponentState::Catalog;
  }
#endif

  return esp32core::encodeComponentStatus(entries, count, output, capacity);
}


// A single runtime edit is a compare-and-swap transaction. All pin changes
// occur in the Arduino loop (the HTTP handler), never in BLE ISR/callbacks.
// If begin() fails after a stopped module changes pins, restore the old lease.
// Deliberately no NVS persistence: on reboot the audited compile-time profile
// is authoritative again until an explicit owner-authorized rewire.
bool applyComponentPins(const char* catalogId, uint8_t from1, uint8_t from2,
                        uint8_t to1, uint8_t to2, void*) {
  if (!catalogId) return false;
  const auto available = [](uint8_t pin, const char* owner) {
    const auto* lease = pinManager.lease(pin);
    const char* system = pinManager.systemOwner(pin);
    return (!lease || std::strcmp(lease->owner, owner) == 0) &&
           (!system || std::strcmp(system, owner) == 0);
  };
#if COMPI_BUILD_DHT22
  if (std::strcmp(catalogId, "dht22") == 0) {
    if (from2 != 0 || to2 != 0 || esp32core::PinManager::reservedForRgb(to1) ||
        !esp32core::PinManager::outputCapable(to1) || !available(to1, dht22Module.id())) return false;
    if (!dht22Module.ready()) {
      if (from1 != 0) return false;
      const uint8_t original = dht22Module.pin();
      if (!dht22Module.configurePin(to1)) return false;
      liveDhtPin = to1;
      if (dht22Module.begin(pinManager)) return true;
      dht22Module.configurePin(original);
      liveDhtPin = original;
      return false;
    }
    if (dht22Module.pin() != from1) return false;
    if (from1 == to1) return true;
    dht22Module.stop(pinManager);
    if (!dht22Module.configurePin(to1)) return false; // preflight ensures this
    liveDhtPin = to1; // the physical bit-bang driver must use the new GPIO
    if (dht22Module.begin(pinManager)) return true;
    dht22Module.configurePin(from1);
    liveDhtPin = from1;
    dht22Module.begin(pinManager);
    return false;
  }
#endif
#if COMPI_BUILD_SOIL
  if (std::strcmp(catalogId, "soil_moisture") == 0) {
    if (from2 != 0 || to2 != 0 || esp32core::PinManager::reservedForRgb(to1) ||
        !esp32core::PinManager::analogInputCapable(to1) || !available(to1, soilModule.id())) return false;
    if (!soilModule.ready()) {
      if (from1 != 0) return false;
      const uint8_t original = soilModule.pin();
      if (!soilModule.configurePin(to1)) return false;
      if (soilModule.begin(pinManager)) return true;
      soilModule.configurePin(original);
      return false;
    }
    if (soilModule.pin() != from1) return false;
    if (from1 == to1) return true;
    soilModule.stop(pinManager);
    if (!soilModule.configurePin(to1)) return false;
    if (soilModule.begin(pinManager)) return true;
    soilModule.configurePin(from1);
    soilModule.begin(pinManager);
    return false;
  }
#endif
#if COMPI_BUILD_OLED && COMPI_OLED_LIBRARY
  if (std::strcmp(catalogId, "ssd1306_oled") == 0) {
    if (to1 == to2 || esp32core::PinManager::reservedForRgb(to1) ||
        esp32core::PinManager::reservedForRgb(to2) ||
        !esp32core::PinManager::outputCapable(to1) || !esp32core::PinManager::outputCapable(to2) ||
        !available(to1, oledModule.id()) || !available(to2, oledModule.id())) return false;
    if (!oledModule.ready()) {
      if (from1 != 0 || from2 != 0) return false;
      const uint8_t originalSda = oledModule.sda(), originalScl = oledModule.scl();
      if (!oledModule.configurePins(to1, to2)) return false;
      if (oledModule.begin(pinManager)) return true;
      oledModule.configurePins(originalSda, originalScl);
      return false;
    }
    if (oledModule.sda() != from1 || oledModule.scl() != from2) return false;
    if (from1 == to1 && from2 == to2) return true;
    oledModule.stop(pinManager);
    if (!oledModule.configurePins(to1, to2)) return false;
    if (oledModule.begin(pinManager)) return true;
    oledModule.configurePins(from1, from2);
    oledModule.begin(pinManager);
    return false;
  }
#endif
  return false;  // Build did not include this optional physical component.
}

esp32core::LocalWifiControl wifiLab(pinManager, stateString, applyRgbCommand,
    applyRgbRuntimeCommand, []() { return deviceIdentity.value(); });

void stopScriptOnManualBle() { wifiLab.stopScriptFromBle(); }

void processEnrollmentFrames() {
  uint16_t rejected = 0;
  portENTER_CRITICAL(&bleEnrollMux);
  rejected = bleEnrollRejected;
  bleEnrollRejected = 0;
  portEXIT_CRITICAL(&bleEnrollMux);
  if (rejected) publishControlReply("ERR malformed");
  for (uint8_t i = 0; i < 4; ++i) {
    char buffer[kEnrollLineMax + 1] = {};
    portENTER_CRITICAL(&bleEnrollMux);
    const bool found = enrollInbox.pop(buffer);
    portEXIT_CRITICAL(&bleEnrollMux);
    if (!found) break;
    // The owner session owns all state transitions; the loop is the sole
    // caller, so no locking is needed around it.
    char reply[kEnrollLineMax + 1] = {};
    if (!ownerSession.handleFrame(buffer, reply, sizeof(reply))) {
      publishControlReply("ERR reply_too_long");
      continue;
    }
    syncBleAuthState();
    publishControlReply(reply);
  }
}

void processBleCommands() {
  processEnrollmentFrames();
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

// ---------------------------------------------------------------------------
// Physical owner window (SEG.1b-2b).
//
// A long BOOT-button hold opens a time-boxed enrollment window (or, when an
// owner already exists, a recovery window). The window issues a fresh one-time
// code that is printed ONLY to the local USB serial console: the code is a
// transient bootstrap secret and is NEVER sent over BLE, shown in exports or
// logged elsewhere. Physical validation is PENDING_HARDWARE_VALIDATION.
// ---------------------------------------------------------------------------
constexpr uint32_t kPhysicalWindowMs = 120000;  // matches OwnerEnrollment
constexpr uint8_t kBootButtonPin = 0;
constexpr uint32_t kBootHoldMs = 3000;
uint32_t bootPressedMs = 0;
bool bootWindowOpened = false;

void openPhysicalWindow(uint32_t now) {
  char code[esp32core::OwnerEnrollment::kOneTimeCodeLength + 1] = {};
  if (!ownerSession.generateOneTimeCode(code)) {
    Serial.println("WARN: RNG unavailable; physical window not opened");
    return;
  }
  const bool recovery = ownerSession.hasOwner();
  const bool ok = recovery
      ? ownerSession.openRecoveryWindow(now, kPhysicalWindowMs, code)
      : ownerSession.openPairingWindow(now, kPhysicalWindowMs, code);
  if (!ok) {
    Serial.println("Physical window request refused");
    return;
  }
  Serial.print(recovery ? "PHYSICAL RECOVERY WINDOW open. One-time code (local USB only): "
                        : "PHYSICAL PAIRING WINDOW open. One-time code (local USB only): ");
  Serial.println(code);
}

void servePhysicalWindow(uint32_t now) {
  const bool pressed = digitalRead(kBootButtonPin) == LOW;
  if (!pressed) {
    bootPressedMs = 0;
    bootWindowOpened = false;
    return;
  }
  if (bootPressedMs == 0) bootPressedMs = now;
  if (!bootWindowOpened && static_cast<uint32_t>(now - bootPressedMs) >= kBootHoldMs) {
    bootWindowOpened = true;
    openPhysicalWindow(now);
  }
}

void setup() {
  Serial.begin(115200);
  Serial.println("\n--- COMPI ESP32 LAB V16 ---");

  if (!deviceIdentity.begin()) {
    Serial.println("ERROR: V4 identity NVS unavailable; control services disabled");
    while (true) delay(1000);
  }

  Serial.println("BOOT: device identity NVS ready");

  // Fail-closed: without usable owner storage the privileged transport must
  // stay disabled rather than falling back to a weaker mode.
  if (!ownerSession.begin()) {
    Serial.println("ERROR: owner store unavailable; control services disabled");
    while (true) delay(1000);
  }

  Serial.println("BOOT: owner NVS ready");
  pinMode(kBootButtonPin, INPUT_PULLUP);

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

  if (!moduleRegistry.add(rgbModule)) {
    Serial.println("ERROR: RGB module failed to reserve/attach PWM");
    while (true) delay(1000);
  }
#if COMPI_ENABLE_DHT22
  moduleRegistry.add(dht22Module);
#endif
#if COMPI_ENABLE_SOIL
  moduleRegistry.add(soilModule);
#endif
#if COMPI_ENABLE_OLED && COMPI_OLED_LIBRARY
  moduleRegistry.add(oledModule);
#endif
  if (!moduleRegistry.startAll(pinManager)) {
    Serial.println("ERROR: module start failed; control services disabled");
    while (true) delay(1000);
  }

  Serial.println("BOOT: firmware modules started");
  wifiLab.setComponentTelemetry(collectComponentTelemetry, nullptr);
  wifiLab.setComponentStatus(collectComponentStatus, nullptr);
  wifiLab.setComponentApply(applyComponentPins, nullptr);
  wifiLab.begin();
  Serial.print("BOOT: Wi-Fi mode=");
  Serial.print(static_cast<int>(WiFi.getMode()));
  Serial.print(" AP-IP=");
  Serial.println(WiFi.softAPIP());

  BLEDevice::init(DEVICE_NAME);
  // STATE frames can be longer than the 20-byte default notification payload.
  // Android requests 128 as well; peers must negotiate before subscribing.
  BLEDevice::setMTU(128);
  // Just-works Secure Connections bonding: provides an encrypted channel only.
  // It NEVER authorizes; the owner key must still be presented over that link.
  BLESecurity securitySetup;
  securitySetup.setCapability(ESP_IO_CAP_NONE);
  securitySetup.setAuthenticationMode(ESP_LE_AUTH_REQ_SC_BOND);
  securitySetup.setInitEncryptionKey(ESP_BLE_ENC_KEY_MASK | ESP_BLE_ID_KEY_MASK);
  BLEDevice::setSecurityCallbacks(new BleLinkSecurityCallbacks());
  // Arduino-ESP32 3.0.7 exposes neither BLEDevice::setEncryptionLevel nor
  // BLESecurity::setEncryptionLevel. Authentication mode and key distribution
  // above configure secure bonding; owner commands remain fail-closed until
  // OwnerEnrollmentSession authenticates their cryptographic session.
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
      GPIO_STATUS_UUID, BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY);
  gpioStatusCharacteristic->addDescriptor(new BLE2902());

  service->start();
  BLEAdvertising* advertising = BLEDevice::getAdvertising();
  // 128-bit GATT service remains in primary announcement. The name
  // occupies a separate scan response, not the same 31-byte BLE packet.
  advertising->addServiceUUID(SERVICE_UUID);
  BLEAdvertisementData scanResponse;
  scanResponse.setName(DEVICE_NAME);
  advertising->setScanResponse(true);
  advertising->setScanResponseData(scanResponse);
  advertising->setMinPreferred(0x06);
  advertising->setMaxPreferred(0x12);
  BLEDevice::startAdvertising();

  publishState();
  Serial.println("BOOT: BLE advertising RGB-ESP32, service 4b9d0001");
  Serial.print("BOOT: after BLE init, AP-IP=");
  Serial.println(WiFi.softAPIP());
}

void loop() {
  uint32_t now = millis();
  if (restartBleAdvertising && !connected &&
      static_cast<uint32_t>(now - bleDisconnectMs) >= 180u) {
    restartBleAdvertising = false;
    BLEDevice::startAdvertising();
    Serial.println("BOOT: BLE reannounced after disconnect");
  }
  processBleCommands();
  ownerSession.tick(now);
  servePhysicalWindow(now);
  wifiLab.tick(now);
  moduleRegistry.tickAll(millis());
  // Modules activated at runtime are not in the boot-time registry; tick them
  // explicitly, only when their leases are active, from this same loop thread.
#if COMPI_BUILD_DHT22
  if (dht22Module.ready() && moduleRegistry.find(dht22Module.id()) == nullptr)
    dht22Module.tick(now);
#endif
#if COMPI_BUILD_SOIL
  if (soilModule.ready() && moduleRegistry.find(soilModule.id()) == nullptr)
    soilModule.tick(now);
#endif
#if COMPI_BUILD_OLED && COMPI_OLED_LIBRARY
  if (oledModule.ready() && moduleRegistry.find(oledModule.id()) == nullptr)
    oledModule.tick(now);
#endif
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
