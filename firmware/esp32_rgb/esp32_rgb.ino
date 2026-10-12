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
#include "core/RgbCommandInbox.h"
#include "core/DeviceIdentity.h"
#include "core/RgbSettingsStore.h"
#include "core/PhysicalProvisioningMode.h"

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
bool connectedPreviously = false;
uint32_t physicalProvisionHoldStarted = 0;
uint32_t effectStarted = 0;
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
    Serial.println("BLE connected");
  }
  void onDisconnect(BLEServer*) override {
    connected = false;
    Serial.println("BLE disconnected");
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
    rgbPendingPersist = {
      rgb.r, rgb.g, rgb.b, rgb.brightness,
      rgb.power ? uint8_t{1} : uint8_t{0},
      static_cast<uint8_t>(rgb.mode), rgb.speed
    };
    rgbDirty = true;
    rgbLastChangedMs = millis();
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
    // The main loop validates again and owns RGB PWM, state notifications,
    // script interruption and diagnostic replies. Never touch them here.
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
    applyRgbRuntimeCommand, []() { return deviceIdentity.value(); });


void stopScriptOnManualBle() { wifiLab.stopScriptFromBle(); }

void processBleCommands() {
  uint16_t rejected = 0;
  portENTER_CRITICAL(&bleInboxMux);
  rejected = bleRejected;
  bleRejected = 0;
  portEXIT_CRITICAL(&bleInboxMux);
  if (rejected) reject(); // one bounded diagnostic, not a BLE callback-side write.
  // A bounded budget prevents a flood of BLE commands starving network/ADC.
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
    // Only VALID manual operations interrupt an active bounded script.
    if (parsed.action != esp32core::RgbAction::Get) stopScriptOnManualBle();
    if (!applyRgbCommand(String(buffer))) reject();
  }
}

void setup() {
  Serial.begin(115200);
  if (!deviceIdentity.begin()) {
    Serial.println("ERROR: V4 identity NVS unavailable; control services disabled");
    while (true) delay(1000);
  }
  // Only personalized firmware can enter Wi-Fi onboarding. This runs BEFORE
  // legacy BLE/GATT and SoftAP are started, preventing duplicate BLE stacks.
  if (esp32core::factory::beginIfPhysicalRequest()) return;
#if RGB_V4_PERSONALIZED
  pinMode(0, INPUT_PULLUP); // BOOT, observed only after the ESP32 has started.
#endif
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

  // Optional V4 identity characteristic. Public identifier only; no
  // password, token, PoP or ownership/authentication claim.
  BLECharacteristic* publicId = service->createCharacteristic(
      PUBLIC_ID_UUID, BLECharacteristic::PROPERTY_READ);
  publicId->setValue(deviceIdentity.value().c_str());
  // Optional read-only GPIO occupancy frame. No BLE GPIO writes, script
  // execution, credentials or Wi-Fi provisioning are exposed.
  gpioStatusCharacteristic = service->createCharacteristic(
      GPIO_STATUS_UUID, BLECharacteristic::PROPERTY_READ);
  service->start();

  BLEAdvertising* advertising = BLEDevice::getAdvertising();
  advertising->addServiceUUID(SERVICE_UUID);
  advertising->setScanResponse(true);
  BLEDevice::startAdvertising();
  Serial.println("RGB-ESP32 ready (laboratory; BLE not authenticated)");
  wifiLab.begin();
  char gpioFrame[96] = {};
  if (wifiLab.writeBlePinSnapshot(gpioFrame) && gpioStatusCharacteristic) {
    gpioStatusCharacteristic->setValue(gpioFrame);
  }
}

void loop() {
  if (esp32core::factory::tick()) {
    delay(20);
    return;
  }
#if RGB_V4_PERSONALIZED
  // BOOT must be held AFTER power-on, for 5 s. It is a recovery gesture,
  // not a network command. GPIO0 remains excluded from PinManager leases.
  if (digitalRead(0) == LOW) {
    if (physicalProvisionHoldStarted == 0) physicalProvisionHoldStarted = millis();
    else if (static_cast<uint32_t>(millis() - physicalProvisionHoldStarted) >= 5000U) {
      Serial.println("Physical provisioning request accepted; restarting");
      esp32core::factory::requestAfterPhysicalHold();
    }
  } else {
    physicalProvisionHoldStarted = 0;
  }
#endif
  if (!connected && connectedPreviously) {
    delay(120);
    BLEDevice::startAdvertising();
    connectedPreviously = false;
  }
  if (connected) connectedPreviously = true;

  processBleCommands();
  moduleRegistry.tickAll(millis());
  wifiLab.tick(millis());
  const uint32_t telemetryNow = millis();
  if (gpioStatusCharacteristic &&
      static_cast<uint32_t>(telemetryNow - lastGpioBleUpdateMs) >= 2000U) {
    char gpioFrame[96] = {};
    if (wifiLab.writeBlePinSnapshot(gpioFrame)) {
      gpioStatusCharacteristic->setValue(gpioFrame);
    }
    lastGpioBleUpdateMs = telemetryNow;
  }
  persistRgbIfDue(millis());
  delay(5);
}
