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
#include "core/EnvironmentalVoicePolicy.h"
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
#include "core/EnvironmentalRgbPolicy.h"
#include "core/BleEnvironmentalCommand.h"
#include "core/BleSnapshotFramer.h"
#include "core/BleLdrCalibration.h"
#include "modules/Dht22Module.h"
#include "core/DhtFrameCodec.h"
#include "core/DhtWireDiagnostics.h"
#include "core/DhtProbeCore.h"
#include "modules/SoilMoistureModule.h"
#include "modules/LdrLightModule.h"
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
#ifndef COMPI_BUILD_DHT11
#define COMPI_BUILD_DHT11 1
#endif
#ifndef COMPI_ENABLE_DHT11
// The user's ESP32 sensor on GPIO16 starts on boot. An absent sensor
// reports invalid telemetry rather than inventing temperature or humidity.
#define COMPI_ENABLE_DHT11 1
#endif
#ifndef COMPI_DHT11_PIN
#define COMPI_DHT11_PIN 16
#endif
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
#ifndef COMPI_BUILD_LDR
#define COMPI_BUILD_LDR 1
#endif
#ifndef COMPI_ENABLE_LDR
// An ADC lease alone does not prove a physical LDR is connected.
// Start only after explicit authorized configuration via /components/apply.
#define COMPI_ENABLE_LDR 0
#endif
#ifndef COMPI_LDR_PIN
#define COMPI_LDR_PIN 36
#endif
#ifndef COMPI_ENABLE_OLED
// Lab profile: enable the attached I2C SSD1306 at boot. The OLED remains
// optional: absent hardware must NEVER prevent Wi-Fi, BLE or DHT11 from booting.
#define COMPI_ENABLE_OLED 1
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

// Activate only after checking electrical speaker output and UART wiring.
// GPIO16 is already occupied by DHT11; never reuse it for this player.
#ifndef COMPI_ENABLE_DFPLAYER
#define COMPI_ENABLE_DFPLAYER 0
#endif
#ifndef COMPI_DFPLAYER_RX
#define COMPI_DFPLAYER_RX 18
#endif
#ifndef COMPI_DFPLAYER_TX
#define COMPI_DFPLAYER_TX 17
#endif
constexpr uint8_t LED_R = 27, LED_G = 25, LED_B = 26;
// Runtime-only RGB mapping. Kept in sync with the module after an authorized
// transaction; fresh boot always returns to the audited pins above.
uint8_t liveRgbPins[3] = {LED_R, LED_G, LED_B};
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
esp32core::BleSnapshotFramer bleSnapshot;
uint32_t lastBleSnapshotPartAt = 0;
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

#if COMPI_BUILD_DHT11 || COMPI_BUILD_DHT22
// Read GPIO edges directly; pulseIn(pin, LOW) can SKIP the initial
// 80-us response when it starts while the line is already low.
// Every request is open-drain electrically: after the start-low pulse,
// switch to INPUT_PULLUP rather than actively driving HIGH into a sensor
// that may already be pulling the wire down.
bool dhtWaitLevel(uint8_t pin, int level, uint32_t timeoutUs) {
  const uint32_t started = micros();
  while (digitalRead(pin) != level) {
    if (static_cast<uint32_t>(micros() - started) >= timeoutUs) return false;
  }
  return true;
}

bool readDhtBits(uint8_t pin, uint16_t startLowMs, uint8_t (&data)[5],
                 esp32core::DhtWireFault& fault) {
  fault = esp32core::DhtWireFault::NotAttempted;
  pinMode(pin, INPUT_PULLUP);
  delayMicroseconds(40);
  if (digitalRead(pin) == LOW) {
    fault = esp32core::DhtWireFault::LineHeldLow;
    return false;
  }
  pinMode(pin, OUTPUT);
  digitalWrite(pin, LOW);
  delay(startLowMs);
  pinMode(pin, INPUT_PULLUP); // release the line, never push it HIGH
  delayMicroseconds(4);
  if (!dhtWaitLevel(pin, LOW, 180)) {
    fault = esp32core::DhtWireFault::NoResponse;
    return false;
  }
  if (!dhtWaitLevel(pin, HIGH, 180) || !dhtWaitLevel(pin, LOW, 180)) {
    fault = esp32core::DhtWireFault::ResponseTimeout;
    return false;
  }
  for (uint8_t i = 0; i < 40; ++i) {
    if (!dhtWaitLevel(pin, HIGH, 140)) {
      fault = esp32core::DhtWireFault::BitTimeout;
      return false;
    }
    const uint32_t highStarted = micros();
    if (!dhtWaitLevel(pin, LOW, 140)) {
      fault = esp32core::DhtWireFault::BitTimeout;
      return false;
    }
    bool bit = false;
    if (!esp32core::dhtDecodeHighPulse(
            static_cast<uint32_t>(micros() - highStarted), bit)) {
      fault = esp32core::DhtWireFault::BitTimeout;
      return false;
    }
    data[i / 8] = static_cast<uint8_t>((data[i / 8] << 1) | (bit ? 1 : 0));
  }
  fault = esp32core::DhtWireFault::Ok;
  return true;
}

esp32core::DhtWireFault dhtDecodeReading(uint8_t pin, uint16_t startLowMs,
    esp32core::DhtModel model, esp32core::Dht22Module::Reading& output) {
  uint8_t data[5] = {};
  esp32core::DhtWireFault fault = esp32core::DhtWireFault::NotAttempted;
  if (!readDhtBits(pin, startLowMs, data, fault)) return fault;
  if (static_cast<uint8_t>(data[0] + data[1] + data[2] + data[3]) != data[4])
    return esp32core::DhtWireFault::BadChecksum;
  const auto decoded = esp32core::decodeDhtFrame(data, model);
  if (!decoded.ok) return esp32core::DhtWireFault::InvalidPayload;
  output = {true, decoded.humidity, decoded.temperature};
  return esp32core::DhtWireFault::Ok;
}
#endif

#if COMPI_BUILD_DHT11
uint8_t liveDht11Pin = COMPI_DHT11_PIN;
esp32core::DhtWireFault dht11LastFault = esp32core::DhtWireFault::NotAttempted;
uint8_t dht11CapturedEdges = 0;
// This is the same core used by the successful isolated probe. Only the
// platform-specific read IO differs. No Wi-Fi/BLE work occurs mid-capture.
struct CompiDht11Io {
  uint8_t pin;
  bool level() const { return digitalRead(pin) == HIGH; }
  uint32_t micros() const { return ::micros(); }
  void spin() const { delayMicroseconds(1); }
  void driveLowMs(uint16_t duration) const {
    digitalWrite(pin, LOW);
    pinMode(pin, OUTPUT);
    delay(duration);
  }
  void release() const { pinMode(pin, INPUT_PULLUP); }
  // Keep interrupts enabled for the 20ms START. Mask only the DHT11 frame
  // (~4ms once per 2s) on this CPU to avoid scheduler-induced missed edges.
  // A scoped guard in DhtProbeCore always restores interrupts on every exit.
  void beginCapture() const { noInterrupts(); }
  void endCapture() const { interrupts(); }
};
esp32core::Dht22Module::Reading dht11Read(void*) {
  CompiDht11Io io{liveDht11Pin};
  const compi_probe::Capture capture = compi_probe::sample(io);
  dht11CapturedEdges = capture.edges;
  switch (capture.verdict) {
    case compi_probe::Verdict::Ok:
      dht11LastFault = esp32core::DhtWireFault::Ok;
      return {true, capture.humidity, capture.temperature};
    case compi_probe::Verdict::LineLow:
      dht11LastFault = esp32core::DhtWireFault::LineHeldLow;
      break;
    case compi_probe::Verdict::NoResponse:
      dht11LastFault = esp32core::DhtWireFault::NoResponse;
      break;
    case compi_probe::Verdict::SyncIncomplete:
      dht11LastFault = esp32core::DhtWireFault::ResponseTimeout;
      break;
    case compi_probe::Verdict::FrameIncomplete:
      dht11LastFault = esp32core::DhtWireFault::BitTimeout;
      break;
    case compi_probe::Verdict::Checksum:
      dht11LastFault = esp32core::DhtWireFault::BadChecksum;
      break;
    case compi_probe::Verdict::Payload:
      dht11LastFault = esp32core::DhtWireFault::InvalidPayload;
      break;
  }
  return {false, 0.0f, 0.0f};
}
#endif
#if COMPI_BUILD_DHT22
uint8_t liveDhtPin = COMPI_DHT22_PIN;
esp32core::DhtWireFault dht22LastFault = esp32core::DhtWireFault::NotAttempted;
esp32core::Dht22Module::Reading dht22Read(void*) {
  esp32core::Dht22Module::Reading reading{false, 0.0f, 0.0f};
  dht22LastFault = dhtDecodeReading(liveDhtPin, 2, esp32core::DhtModel::DHT22, reading);
  return reading;
}
#endif

#if COMPI_BUILD_SOIL
int soilAdcRead(uint8_t pin, void*) { return analogRead(pin); }
#endif  // COMPI_ENABLE_SOIL
#if COMPI_BUILD_LDR
int ldrAdcRead(uint8_t pin, void*) { return analogRead(pin); }
#endif

#if COMPI_BUILD_OLED
#include <Wire.h>
#if COMPI_ENABLE_OLED
// Use a real include: Arduino CLI's library resolver does not discover a
// library when it appears only behind __has_include.
// If missing, Arduino prints the header name and the build stops.
#include <Adafruit_SSD1306.h>
#define COMPI_OLED_LIBRARY 1
Adafruit_SSD1306 oledDriver(128, 64, &Wire, -1);
bool oledInit(uint8_t address, uint8_t sda, uint8_t scl, void*) {
  Wire.begin(sda, scl);
  // SSD1306.begin() can succeed without an attached display. Require an
  // actual I2C ACK before reporting this optional component as operational.
  Wire.beginTransmission(address);
  if (Wire.endTransmission() != 0) return false;
  // Keep the explicitly selected SDA/SCL pins; do not call Wire.begin() again.
  if (!oledDriver.begin(SSD1306_SWITCHCAPVCC, address, false, false)) return false;
  oledDriver.setTextColor(SSD1306_WHITE);
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
#define COMPI_OLED_LIBRARY 0
#endif
#endif  // COMPI_BUILD_OLED

esp32core::RgbSettingsStore rgbStorage;
bool rgbDirty = false;
// Only explicit manual RGB choices become persistent. Temporary scripts and
// automation transitions must never overwrite the user's restored preference.
esp32core::RgbSaved rgbPendingPersist{};
bool rgbPersistenceAttempted = false;
// Pure policy, no competing PWM driver. The authenticated Wi-Fi transport
// controls opt-in mode, and manual BLE/Wi-Fi commands cancel it.
esp32core::EnvironmentalRgbPolicy environmentRgbPolicy;
uint32_t lastEnvironmentalRgbAt = 0;
void stopEnvironmentalRgbOnManual();
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
  for (uint8_t i = 0; i < 3; i++) {
    ledcWrite(liveRgbPins[i], COMMON_ANODE ? (255 - values[i]) : values[i]);
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
  if (persistManualChoice && command.action != esp32core::RgbAction::Get)
    stopEnvironmentalRgbOnManual();
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
    char kind = 0;
    uint16_t requestId = 0;
    esp32core::BleLdrCalibration calibration{};
    const bool valid = esp32core::parseRgbCommand(command.c_str(), parsed) ||
                       esp32core::isBleEnvironmentalCommand(command.c_str()) ||
                       esp32core::parseBleSnapshotRequest(command.c_str(),
                                                         kind, requestId) ||
                       esp32core::parseBleLdrCalibration(command.c_str(), calibration);
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

#if COMPI_BUILD_DHT11
esp32core::Dht22Module dht11Module(COMPI_DHT11_PIN, dht11Read, nullptr, 2000, "sensor.dht11");
#endif
#if COMPI_BUILD_DHT22
esp32core::Dht22Module dht22Module(COMPI_DHT22_PIN, dht22Read, nullptr, 2000);
#endif
#if COMPI_BUILD_SOIL
esp32core::SoilMoistureModule soilModule(COMPI_SOIL_PIN, soilAdcRead, nullptr, 1000);
#endif
#if COMPI_BUILD_LDR
esp32core::LdrLightModule ldrModule(COMPI_LDR_PIN, ldrAdcRead, nullptr, 500);
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
#if COMPI_BUILD_DHT11
  if (dht11Module.ready()) {
    entries[count++] = dht11Module.humidityReading();
    entries[count++] = dht11Module.temperatureReading();
  }
#endif
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
#if COMPI_BUILD_LDR
  if (ldrModule.ready()) {
    entries[count++] = ldrModule.rawReading();
    entries[count++] = ldrModule.lightReading();
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
    entry.active = rgbModule.ready();
    entry.pinCount = entry.active ? 3 : 0;
    entry.state = entry.active ? esp32core::ComponentState::Active
                               : esp32core::ComponentState::Error;
  }

#if COMPI_BUILD_DHT11
  {
    esp32core::ComponentStatusEntry& entry = entries[count++];
    entry = {};
    entry.id = dht11Module.id();
    entry.type = esp32core::ComponentType::Sensor;
    entry.interfaceType = esp32core::HardwareInterface::OneWire;
    entry.pins[0] = dht11Module.pin();
    entry.active = dht11Module.ready();
    entry.pinCount = entry.active ? 1 : 0;
    entry.state = entry.active ? esp32core::ComponentState::Active
                               : esp32core::ComponentState::Catalog;
    entry.diagnostic = esp32core::dhtWireFaultName(dht11LastFault);
    entry.failures = dht11Module.consecutiveFailures();
  }
#endif
#if COMPI_BUILD_DHT22
  {
    esp32core::ComponentStatusEntry& entry = entries[count++];
    entry = {};
    entry.id = dht22Module.id();
    entry.type = esp32core::ComponentType::Sensor;
    entry.interfaceType = esp32core::HardwareInterface::OneWire;
    entry.pins[0] = dht22Module.pin();
    entry.active = dht22Module.ready();
    entry.pinCount = entry.active ? 1 : 0;
    entry.state = entry.active ? esp32core::ComponentState::Active
                               : esp32core::ComponentState::Catalog;
    entry.diagnostic = esp32core::dhtWireFaultName(dht22LastFault);
    entry.failures = dht22Module.consecutiveFailures();
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
    entry.active = soilModule.ready();
    entry.pinCount = entry.active ? 1 : 0;
    entry.state = entry.active ? esp32core::ComponentState::Active
                               : esp32core::ComponentState::Catalog;
  }
#endif
#if COMPI_BUILD_LDR
  {
    esp32core::ComponentStatusEntry& entry = entries[count++];
    entry = {};
    entry.id = ldrModule.id();
    entry.type = esp32core::ComponentType::Sensor;
    entry.interfaceType = esp32core::HardwareInterface::Adc;
    entry.pins[0] = ldrModule.pin();
    entry.active = ldrModule.ready();
    entry.pinCount = entry.active ? 1 : 0;
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
    entry.active = oledModule.ready();
    entry.pinCount = entry.active ? 2 : 0;
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
                        uint8_t to1, uint8_t to2, uint8_t from3, uint8_t to3,
                        void*) {
  if (!catalogId) return false;
  const auto available = [](uint8_t pin, const char* owner) {
    const auto* lease = pinManager.lease(pin);
    const char* system = pinManager.systemOwner(pin);
    return (!lease || std::strcmp(lease->owner, owner) == 0) &&
           (!system || std::strcmp(system, owner) == 0);
  };
  if (std::strcmp(catalogId, "led_rgb") == 0) {
    if (!rgbModule.ready() || rgbModule.redPin() != from1 ||
        rgbModule.greenPin() != from2 || rgbModule.bluePin() != from3 ||
        to1 == to2 || to1 == to3 || to2 == to3 ||
        !esp32core::PinManager::outputCapable(to1) ||
        !esp32core::PinManager::outputCapable(to2) ||
        !esp32core::PinManager::outputCapable(to3) ||
        !available(to1, rgbModule.id()) || !available(to2, rgbModule.id()) ||
        !available(to3, rgbModule.id())) return false;
    if (from1 == to1 && from2 == to2 && from3 == to3) return true;
    // All RGB writes and command processing run in the Arduino loop, so
    // no BLE callback can mutate PWM while this transaction reattaches.
    rgbModule.stop(pinManager);
    if (rgbModule.configurePins(to1, to2, to3)) {
      liveRgbPins[0] = to1; liveRgbPins[1] = to2; liveRgbPins[2] = to3;
      if (rgbModule.begin(pinManager)) return true;
    }
    // Never acknowledge a failed pin reassignment. Restore the last verified
    // runtime mapping; if even rollback fails, report RGB inactive in status.
    rgbModule.configurePins(from1, from2, from3);
    liveRgbPins[0] = from1; liveRgbPins[1] = from2; liveRgbPins[2] = from3;
    rgbModule.begin(pinManager);
    return false;
  }
#if COMPI_BUILD_DHT11 && COMPI_BUILD_DHT22
  // DHT11 and DHT22 use the same telemetry fields. Allow ONE active model.
  // Switch atomically with GPIO lease rollback to avoid ghost owners/data.
  if (std::strcmp(catalogId, "dht11") == 0 ||
      std::strcmp(catalogId, "dht22") == 0) {
    const bool is11 = std::strcmp(catalogId, "dht11") == 0;
    esp32core::Dht22Module& target = is11 ? dht11Module : dht22Module;
    esp32core::Dht22Module& other = is11 ? dht22Module : dht11Module;
    uint8_t& activePin = is11 ? liveDht11Pin : liveDhtPin;
    const bool wasReady = target.ready(), otherReady = other.ready();
    const uint8_t oldPin = target.pin(), otherPin = other.pin();
    if (from2 != 0 || to2 != 0 || from3 != 0 || to3 != 0 ||
        esp32core::PinManager::reservedForRgb(to1) ||
        !esp32core::PinManager::outputCapable(to1) ||
        (wasReady ? from1 != oldPin : from1 != 0)) return false;
    const auto* lease = pinManager.lease(to1);
    if (lease && std::strcmp(lease->owner, target.id()) != 0 &&
        std::strcmp(lease->owner, other.id()) != 0) return false;
    const char* system = pinManager.systemOwner(to1);
    if (system && std::strcmp(system, target.id()) != 0 &&
        std::strcmp(system, other.id()) != 0) return false;
    if (wasReady && oldPin == to1 && !otherReady) return true;
    if (wasReady) target.stop(pinManager);
    if (otherReady) other.stop(pinManager);
    if (target.configurePin(to1)) {
      activePin = to1;
      if (target.begin(pinManager)) return true;
    }
    // A failed transfer restores both the former pin and the owner lease.
    target.configurePin(oldPin);
    activePin = oldPin;
    if (wasReady) target.begin(pinManager);
    if (otherReady) other.begin(pinManager);
    return false;
  }
#else
#if COMPI_BUILD_DHT11
  if (std::strcmp(catalogId, "dht11") == 0) {
    if (from2 != 0 || to2 != 0 || esp32core::PinManager::reservedForRgb(to1) ||
        !esp32core::PinManager::outputCapable(to1) ||
        !available(to1, dht11Module.id()) ||
        (dht11Module.ready() ? from1 != dht11Module.pin() : from1 != 0)) return false;
    if (dht11Module.ready() && from1 == to1) return true;
    const bool wasReady = dht11Module.ready();
    const uint8_t oldPin = dht11Module.pin();
    if (wasReady) dht11Module.stop(pinManager);
    if (dht11Module.configurePin(to1)) {
      liveDht11Pin = to1;
      if (dht11Module.begin(pinManager)) return true;
    }
    dht11Module.configurePin(oldPin);
    liveDht11Pin = oldPin;
    if (wasReady) dht11Module.begin(pinManager);
    return false;
  }
#endif
#if COMPI_BUILD_DHT22
  if (std::strcmp(catalogId, "dht22") == 0) {
    if (from2 != 0 || to2 != 0 || esp32core::PinManager::reservedForRgb(to1) ||
        !esp32core::PinManager::outputCapable(to1) ||
        !available(to1, dht22Module.id()) ||
        (dht22Module.ready() ? from1 != dht22Module.pin() : from1 != 0)) return false;
    if (dht22Module.ready() && from1 == to1) return true;
    const bool wasReady = dht22Module.ready();
    const uint8_t oldPin = dht22Module.pin();
    if (wasReady) dht22Module.stop(pinManager);
    if (dht22Module.configurePin(to1)) {
      liveDhtPin = to1;
      if (dht22Module.begin(pinManager)) return true;
    }
    dht22Module.configurePin(oldPin);
    liveDhtPin = oldPin;
    if (wasReady) dht22Module.begin(pinManager);
    return false;
  }
#endif
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
#if COMPI_BUILD_LDR
  if (std::strcmp(catalogId, "ldr_light") == 0) {
    if (from2 != 0 || to2 != 0 || from3 != 0 || to3 != 0 ||
        esp32core::PinManager::reservedForRgb(to1) ||
        !esp32core::PinManager::analogInputCapable(to1) ||
        !available(to1, ldrModule.id())) return false;
    const bool wasReady = ldrModule.ready();
    const uint8_t oldPin = ldrModule.pin();
    if ((wasReady ? oldPin : 0) != from1) return false;
    if (wasReady && from1 == to1) return true;
    if (wasReady) ldrModule.stop(pinManager);
    if (ldrModule.configurePin(to1) && ldrModule.begin(pinManager)) return true;
    ldrModule.configurePin(oldPin);
    if (wasReady) ldrModule.begin(pinManager);
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

#if COMPI_BUILD_LDR
bool calibrateLdr(uint16_t darkRaw, uint16_t brightRaw, void*) {
  // GPIO ownership and actual ADC sampling are required for calibration.
  return ldrModule.ready() &&
         pinManager.belongsTo(ldrModule.pin(), ldrModule.id()) &&
         ldrModule.rawReading().valid &&
         ldrModule.setCalibration(darkRaw, brightRaw);
}
#endif

esp32core::LocalWifiControl wifiLab(pinManager, stateString, applyRgbCommand,
    applyRgbRuntimeCommand, []() { return deviceIdentity.value(); });

void stopEnvironmentalRgbOnManual() {
  wifiLab.disableEnvironmentalRgb();
  environmentRgbPolicy.reset();
}
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
    char snapshotKind = 0;
    uint16_t snapshotId = 0;
    const bool snapshotRequest = esp32core::parseBleSnapshotRequest(
        buffer, snapshotKind, snapshotId);
    const auto environmental = esp32core::parseBleEnvironmentalCommand(buffer);
    esp32core::BleLdrCalibration calibration{};
    const bool calibrationRequest = esp32core::parseBleLdrCalibration(
        buffer, calibration);
    esp32core::RgbCommand parsed{};
    if (!snapshotRequest && !calibrationRequest &&
        environmental == esp32core::BleEnvironmentalAction::Invalid &&
        !esp32core::parseRgbCommand(buffer, parsed)) {
      reject();
      continue;
    }
    const bool isStop =
        environmental == esp32core::BleEnvironmentalAction::Stop ||
        (environmental == esp32core::BleEnvironmentalAction::Invalid &&
         (parsed.action == esp32core::RgbAction::StopEffect ||
          parsed.action == esp32core::RgbAction::Off));
    const auto channelSource = esp32core::CompiCommandArbiter::mapBleSource(bleAuthState);
    auto evaluation = commandArbiter.evaluate(channelSource, isStop);
    if (!evaluation.allowed) {
      reject();
      continue;
    }
    if (calibrationRequest) {
      const bool applied = wifiLab.calibrateLdr(calibration.darkRaw,
                                                calibration.brightRaw);
      const String ack = String(applied ? "LDR,OK," : "LDR,REJECT,") +
                         String(calibration.requestId);
      publishControlReply(ack.c_str());
      continue;
    }
    if (snapshotRequest) {
      if (bleSnapshot.active()) {
        publishControlReply("SNP,BUSY");
      } else {
        char json[esp32core::BleSnapshotFramer::kPayloadMax + 1] = {};
        const bool statusRequest = snapshotKind == 'S';
        const bool encoded = statusRequest
            ? collectComponentStatus(json, sizeof(json), nullptr)
            : collectComponentTelemetry(json, sizeof(json), nullptr);
        if (!encoded || !bleSnapshot.start(statusRequest
                ? esp32core::BleSnapshotFramer::Kind::Status
                : esp32core::BleSnapshotFramer::Kind::Telemetry,
                json, snapshotId)) {
          publishControlReply("SNP,ERROR");
        }
      }
      continue;
    }
    if (environmental != esp32core::BleEnvironmentalAction::Invalid) {
      if (environmental == esp32core::BleEnvironmentalAction::Start &&
          !wifiLab.startEnvironmentalRgb()) {
        publishControlReply("ENV,BUSY");
      } else {
        if (environmental == esp32core::BleEnvironmentalAction::Stop) {
          wifiLab.disableEnvironmentalRgb();
          environmentRgbPolicy.reset();
        }
        publishControlReply(wifiLab.environmentalRgbEnabled()
                                ? "ENV,ON" : "ENV,OFF");
      }
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

#if COMPI_ENABLE_DFPLAYER
esp32core::EnvironmentalVoicePolicy voicePolicy;
HardwareSerial dfplayerSerial(2);
uint32_t lastVoiceSampleAt = 0;
void dfplayerCommand(uint8_t command, uint16_t parameter) {
  const uint8_t packet[10] = {
    0x7E, 0xFF, 0x06, command, 0x00,
    static_cast<uint8_t>(parameter >> 8),
    static_cast<uint8_t>(parameter & 0xFF), 0, 0, 0xEF
  };
  uint8_t frame[10];
  for (uint8_t i = 0; i < 10; ++i) frame[i] = packet[i];
  const uint16_t checksum = static_cast<uint16_t>(0u -
      (0xFFu + 0x06u + command + frame[4] + frame[5] + frame[6]));
  frame[7] = checksum >> 8;
  frame[8] = checksum & 0xFFu;
  dfplayerSerial.write(frame, sizeof(frame));
}
void dfplayerTrack(uint16_t track) {
  // DFPlayer 'play specified track' command 0x03. Playback is NOT confirmed
  // without BUSY/status feedback from the hardware.
  dfplayerCommand(0x03, track);
}
#endif

void setup() {
#if COMPI_ENABLE_DFPLAYER
  // USB-independent UART2; 16 belongs to DHT11. DFPlayer is externally powered.
  dfplayerSerial.begin(9600, SERIAL_8N1, COMPI_DFPLAYER_RX, COMPI_DFPLAYER_TX);
  delay(250);
  dfplayerCommand(0x06, 1); // volume 1/30 until amplifier power is verified
#endif

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

  // GPIO16/17 can be occupied internally by PSRAM on ESP32-WROVER modules.
  // Protect board-internal IO from accidental DHT reassignment.
  if (psramFound()) {
    pinManager.registerSystemPin(16, "board.psram");
    pinManager.registerSystemPin(17, "board.psram");
    Serial.println("GPIO16/17 reservado: esta placa detecto PSRAM. Usa GPIO32/33.");
  } else {
    Serial.println("GPIO16 disponible en perfil sin PSRAM; requiere prueba electrica real.");
  }
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
#if COMPI_ENABLE_DHT11 && COMPI_BUILD_DHT11
  moduleRegistry.add(dht11Module);
#endif
#if COMPI_ENABLE_DHT22
  moduleRegistry.add(dht22Module);
#endif
#if COMPI_ENABLE_SOIL
  moduleRegistry.add(soilModule);
#endif
#if COMPI_ENABLE_LDR && COMPI_BUILD_LDR
  moduleRegistry.add(ldrModule);
#endif
#if COMPI_ENABLE_OLED && COMPI_OLED_LIBRARY
  moduleRegistry.add(oledModule);
#endif
  // RGB is the single required module. A missing optional OLED or sensor
  // must not suppress SoftAP/BLE or trap the board in an unrecoverable loop.
  if (!moduleRegistry.startRequiredAndOptional(pinManager, 1)) {
    Serial.println("ERROR: required RGB module start failed; control services disabled");
    while (true) delay(1000);
  }
  if (moduleRegistry.unavailableAtBoot() > 0) {
    Serial.print("WARN: optional modules inactive at boot: ");
    Serial.println(moduleRegistry.unavailableAtBoot());
    Serial.println("Control services continue; check wiring via component status.");
  }

#if COMPI_ENABLE_OLED && COMPI_OLED_LIBRARY
  if (oledModule.ready()) {
    oledModule.setText("HOLA, SOY COMPI!\nOLED LISTA  (^_^)");
    if (oledModule.refreshNow(millis())) {
      Serial.println("BOOT: OLED SSD1306 ACK OK, saludo mostrado");
    } else {
      Serial.println("WARN: OLED responde por I2C, pero no confirmo refresco");
    }
  } else {
    Serial.println("WARN: OLED no responde; comprueba 3V3/GND/SDA21/SCL22 y 0x3C");
  }
#elif COMPI_ENABLE_OLED && COMPI_BUILD_OLED
  Serial.println("WARN: falta instalar Adafruit SSD1306 y Adafruit GFX en Arduino IDE");
#endif

  Serial.println("BOOT: required modules started; optional components reported individually");
  wifiLab.setComponentTelemetry(collectComponentTelemetry, nullptr);
  wifiLab.setComponentStatus(collectComponentStatus, nullptr);
  wifiLab.setComponentApply(applyComponentPins, nullptr);
#if COMPI_BUILD_LDR
  wifiLab.setLdrCalibration(calibrateLdr, nullptr);
#endif
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
#if COMPI_ENABLE_DFPLAYER
  if (static_cast<uint32_t>(now - lastVoiceSampleAt) >= 2000u) {
    lastVoiceSampleAt = now;
    esp32core::VoiceSample voiceSample{};
#if COMPI_BUILD_DHT11
    if (dht11Module.ready()) {
      const auto& temp = dht11Module.temperatureReading();
      voiceSample.temperatureFresh = temp.valid &&
          temp.status == esp32core::MeasurementStatus::Fresh &&
          static_cast<uint32_t>(now - temp.timestampMs) <= 10000u;
      voiceSample.temperatureC = temp.value;
    }
#endif
#if COMPI_BUILD_LDR
    if (ldrModule.ready()) {
      const auto& light = ldrModule.lightReading();
      voiceSample.lightFresh = light.valid &&
          light.status == esp32core::MeasurementStatus::Fresh &&
          static_cast<uint32_t>(now - light.timestampMs) <= 10000u;
      voiceSample.lightPercent = light.value;
    }
#endif
    const auto cue = voicePolicy.evaluate(voiceSample, now);
    if (cue != esp32core::VoiceCue::None) {
      dfplayerTrack(static_cast<uint16_t>(cue));
    }
  }
#endif

#if COMPI_ENABLE_OLED && COMPI_OLED_LIBRARY && COMPI_BUILD_DHT11
  // Small independent OLED dashboard: never writes PWM and never blocks
  // the network. Start after the greeting, then refresh every 3 seconds.
  static uint32_t lastOledTelemetryAt = 0;
  if (oledModule.ready() && now >= 8000U &&
      (lastOledTelemetryAt == 0 ||
       static_cast<uint32_t>(now - lastOledTelemetryAt) >= 3000U)) {
    lastOledTelemetryAt = now;
    char oledLines[esp32core::OledSsd1306Module::kMaxTextBytes] = {};
    const auto& temperature = dht11Module.temperatureReading();
    const auto& humidity = dht11Module.humidityReading();
    if (temperature.valid && humidity.valid) {
      std::snprintf(oledLines, sizeof(oledLines), "COMPI - DHT11\nT: %.1f C\nH: %.0f %%",
                    static_cast<double>(temperature.value),
                    static_cast<double>(humidity.value));
    } else {
      std::snprintf(oledLines, sizeof(oledLines), "COMPI - DHT11\nEsperando lectura");
    }
    if (oledModule.setText(oledLines)) oledModule.refreshNow(now);
  }
#endif
  // Modules activated at runtime are not in the boot-time registry; tick them
  // explicitly, only when their leases are active, from this same loop thread.
#if COMPI_BUILD_DHT11
  if (dht11Module.ready() && moduleRegistry.find(dht11Module.id()) == nullptr)
    dht11Module.tick(now);
#endif
#if COMPI_BUILD_DHT22
  if (dht22Module.ready() && moduleRegistry.find(dht22Module.id()) == nullptr)
    dht22Module.tick(now);
#endif
#if COMPI_BUILD_SOIL
  if (soilModule.ready() && moduleRegistry.find(soilModule.id()) == nullptr)
    soilModule.tick(now);
#endif
#if COMPI_BUILD_LDR
  if (ldrModule.ready() && moduleRegistry.find(ldrModule.id()) == nullptr)
    ldrModule.tick(now);
#endif
#if COMPI_BUILD_OLED && COMPI_OLED_LIBRARY
  if (oledModule.ready() && moduleRegistry.find(oledModule.id()) == nullptr)
    oledModule.tick(now);
#endif
  // Single Arduino loop is the only RGB writer. No BLE ISR or sensor module
  // writes PWM; mode is opt-in via authorized SoftAP and manual control cancels.
  if (wifiLab.environmentalRgbMayRun() && rgbModule.ready() &&
      static_cast<uint32_t>(now - lastEnvironmentalRgbAt) >= 1000u) {
    lastEnvironmentalRgbAt = now;
    esp32core::EnvironmentalRgbInput input{};
#if COMPI_BUILD_DHT11
    if (dht11Module.ready()) {
      const auto& measure = dht11Module.temperatureReading();
      input.temperatureFresh = measure.valid &&
          measure.status == esp32core::MeasurementStatus::Fresh;
      input.temperatureC = measure.value;
      input.temperatureAgeMs = static_cast<uint32_t>(now - measure.timestampMs);
    }
#endif
#if COMPI_BUILD_DHT22
    if (!input.temperatureFresh && dht22Module.ready()) {
      const auto& measure = dht22Module.temperatureReading();
      input.temperatureFresh = measure.valid &&
          measure.status == esp32core::MeasurementStatus::Fresh;
      input.temperatureC = measure.value;
      input.temperatureAgeMs = static_cast<uint32_t>(now - measure.timestampMs);
    }
#endif
#if COMPI_BUILD_LDR
    if (ldrModule.ready()) {
      const auto& light = ldrModule.lightReading();
      input.lightFresh = light.valid &&
          light.status == esp32core::MeasurementStatus::Fresh;
      input.lightPercent = light.value;
      input.lightAgeMs = static_cast<uint32_t>(now - light.timestampMs);
    }
#endif
    const auto decision = environmentRgbPolicy.evaluate(input);
    // Do not change RGB when temperature is stale or invalid.
    if (decision.apply &&
        (!rgb.power || rgb.mode != Mode::NONE || rgb.r != decision.red ||
         rgb.g != decision.green || rgb.b != decision.blue ||
         rgb.brightness != decision.brightness)) {
      const String command = "SET," + String(decision.red) + "," +
          String(decision.green) + "," + String(decision.blue) + "," +
          String(decision.brightness);
      applyRgbRuntimeCommand(command);  // never persists a temporary color
    }
  }
  // Send one small owner-only snapshot chunk per loop interval. Never emit
  // before cryptographic owner authorization or after disconnect/revocation.
  if (!connected || !ownerSession.authenticated()) {
    bleSnapshot.cancel();
  } else if (bleSnapshot.active() &&
             static_cast<uint32_t>(now - lastBleSnapshotPartAt) >= 45u) {
    lastBleSnapshotPartAt = now;
    bleSnapshot.tick([](const char* frame, void*) {
      publishControlReply(frame);
    }, nullptr);
  }
  persistRgbIfDue(now);
  // Read-only electrical/protocol evidence on the USB serial monitor (115200).
  // Do not toggle GPIO16/17 to "test" while a sensor is attached.
#if COMPI_BUILD_DHT11
  static uint32_t lastDhtDiagAt = 0;
  if (dht11Module.ready() && static_cast<uint32_t>(now - lastDhtDiagAt) >= 15000U) {
    lastDhtDiagAt = now;
    Serial.printf("DHT11 DATA GPIO%u: %s, edges=%u/82, consecutiveFailures=%u, valid=%u\n",
      static_cast<unsigned>(dht11Module.pin()),
      esp32core::dhtWireFaultName(dht11LastFault),
      static_cast<unsigned>(dht11CapturedEdges),
      static_cast<unsigned>(dht11Module.consecutiveFailures()),
      dht11Module.humidityReading().valid ? 1U : 0U);
  }
#endif

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
