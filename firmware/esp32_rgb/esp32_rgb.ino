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
uint32_t lastFrame = 0;

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

bool parseSet(const String& line, uint8_t& r, uint8_t& g, uint8_t& b, uint8_t& brightness) {
  int rr = -1, gg = -1, bb = -1, br = -1;
  char trailing = 0;
  if (sscanf(line.c_str(), "SET,%d,%d,%d,%d%c", &rr, &gg, &bb, &br, &trailing) != 4) return false;
  if (rr < 0 || rr > 255 || gg < 0 || gg > 255 || bb < 0 || bb > 255 ||
      br < 0 || br > 255) return false;
  r = static_cast<uint8_t>(rr);
  g = static_cast<uint8_t>(gg);
  b = static_cast<uint8_t>(bb);
  brightness = static_cast<uint8_t>(br);
  return true;
}

bool parseEffect(const String& line, Mode& mode, uint16_t& speed) {
  char name[16] = {0};
  int value = 0;
  char trailing = 0;
  if (sscanf(line.c_str(), "FX,%15[^,],%d%c", name, &value, &trailing) != 2) return false;
  if (value < 10 || value > 1000) return false;
  if (strcmp(name, "RAINBOW") == 0) mode = Mode::RAINBOW;
  else if (strcmp(name, "BREATHE") == 0) mode = Mode::BREATHE;
  else if (strcmp(name, "FADE") == 0) mode = Mode::FADE;
  else return false;
  speed = static_cast<uint16_t>(value);
  return true;
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

class CommandCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* characteristic) override {
    String line = characteristic->getValue();
    if (line.length() == 0 || line.length() > 48) { reject(); return; }
    line.trim();
    if (line == "GET") { publishState(); return; }
    if (line == "OFF") { rgb.power = false; renderStatic(); publishState(); return; }
    if (line == "ON") {
      rgb.power = true;
      if (rgb.mode == Mode::NONE) renderStatic();
      publishState();
      return;
    }
    if (line == "FX,STOP") {
      rgb.mode = Mode::NONE;
      renderStatic();
      publishState();
      return;
    }

    uint8_t r, g, b, brightness;
    if (parseSet(line, r, g, b, brightness)) {
      rgb.r = r; rgb.g = g; rgb.b = b; rgb.brightness = brightness;
      rgb.mode = Mode::NONE;
      rgb.power = true;
      renderStatic();
      publishState();
      return;
    }
    Mode mode;
    uint16_t speed;
    if (parseEffect(line, mode, speed)) {
      rgb.mode = mode;
      rgb.speed = speed;
      rgb.power = true;
      effectStarted = millis();
      lastFrame = 0;
      publishState();
      return;
    }
    reject();
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

void setup() {
  Serial.begin(115200);
  if (!ledcAttach(LED_R, 5000, 8) ||
      !ledcAttach(LED_G, 5000, 8) ||
      !ledcAttach(LED_B, 5000, 8)) {
    Serial.println("ERROR: PWM attach failed");
    while (true) delay(1000);
  }
  renderStatic();  // boot OFF

  BLEDevice::init(DEVICE_NAME);
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
}

void loop() {
  if (!connected && connectedPreviously) {
    delay(120);
    BLEDevice::startAdvertising();
    connectedPreviously = false;
  }
  if (connected) connectedPreviously = true;

  uint32_t now = millis();
  if (rgb.mode != Mode::NONE && now - lastFrame >= 25U) {
    lastFrame = now;
    renderEffect(now);
  }
  delay(5);
}
