// RGB Controller — Firmware experimental ESP32 + Arduino-ESP32 3.x.
// IMPORTANTE: BLE sin autenticacion. Solo pruebas de laboratorio.
// GPIO: R=27, G=25, B=26. Resistencias 220-330 ohms individuales.
#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <cstdio>

static constexpr uint8_t PIN_R = 27;
static constexpr uint8_t PIN_G = 25;
static constexpr uint8_t PIN_B = 26;
// Cambiar solo despues de verificar el tipo del LED fisico.
static constexpr bool ANODO_COMUN = true;

static constexpr char DEVICE_NAME[] = "RGB-ESP32";
static constexpr char SERVICE_UUID[] =
    "4b9d0001-7a5e-4d4c-ae88-c8c70d3ab211";
static constexpr char COMMAND_UUID[] =
    "4b9d0002-7a5e-4d4c-ae88-c8c70d3ab211";
static constexpr char STATE_UUID[] =
    "4b9d0003-7a5e-4d4c-ae88-c8c70d3ab211";

struct RgbState {
  uint8_t r = 0;
  uint8_t g = 0;
  uint8_t b = 0;
  uint8_t brightness = 255;
  bool power = false;
};
static RgbState state;
static BLECharacteristic* stateCharacteristic = nullptr;
static volatile bool connected = false;
static bool wasConnected = false;

static void writeChannel(uint8_t pin, uint8_t color, uint8_t brightness) {
  const uint8_t level = (static_cast<uint16_t>(color) * brightness) / 255;
  ledcWrite(pin, ANODO_COMUN ? 255 - level : level);
}

static void render() {
  const uint8_t bright = state.power ? state.brightness : 0;
  writeChannel(PIN_R, state.r, bright);
  writeChannel(PIN_G, state.g, bright);
  writeChannel(PIN_B, state.b, bright);
}

static String statePayload() {
  return "STATE," + String(state.r) + "," + String(state.g) + "," +
         String(state.b) + "," + String(state.brightness) + "," +
         (state.power ? "1" : "0");
}

static void publishState() {
  if (stateCharacteristic == nullptr) return;
  const String payload = statePayload();
  stateCharacteristic->setValue(payload.c_str());
  if (connected) stateCharacteristic->notify();
  Serial.println(payload);
}

static void publishError() {
  if (stateCharacteristic == nullptr) return;
  if (connected) {
    stateCharacteristic->setValue("ERR,INVALID_COMMAND");
    stateCharacteristic->notify();
    // Mantener el valor legible como estado, no como error.
    stateCharacteristic->setValue(statePayload().c_str());
  }
  Serial.println("ERR,INVALID_COMMAND");
}

static bool parseSet(const String& input, uint8_t& r, uint8_t& g,
                     uint8_t& b, uint8_t& brightness) {
  int rr = -1, gg = -1, bb = -1, br = -1;
  char extra = 0;
  if (sscanf(input.c_str(), "SET,%d,%d,%d,%d%c",
             &rr, &gg, &bb, &br, &extra) != 4) return false;
  if (rr < 0 || rr > 255 || gg < 0 || gg > 255 ||
      bb < 0 || bb > 255 || br < 0 || br > 255) return false;
  r = static_cast<uint8_t>(rr);
  g = static_cast<uint8_t>(gg);
  b = static_cast<uint8_t>(bb);
  brightness = static_cast<uint8_t>(br);
  return true;
}

class ConnectionCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* server) override {
    connected = true;
    Serial.println("BLE connected");
  }
  void onDisconnect(BLEServer* server) override {
    connected = false;
    Serial.println("BLE disconnected");
  }
};

class CommandCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* characteristic) override {
    String command = characteristic->getValue();
    command.trim();
    if (command.length() == 0 || command.length() > 48) {
      publishError();
      return;
    }
    if (command == "GET") {
      publishState();
      return;
    }
    if (command == "OFF") {
      state.power = false;
    } else if (command == "ON") {
      state.power = true;
    } else {
      uint8_t r, g, b, brightness;
      if (!parseSet(command, r, g, b, brightness)) {
        publishError();
        return;
      }
      state.r = r;
      state.g = g;
      state.b = b;
      state.brightness = brightness;
      state.power = true;
    }
    render();
    publishState();
  }
};

void setup() {
  Serial.begin(115200);

  if (!ledcAttach(PIN_R, 5000, 8) ||
      !ledcAttach(PIN_G, 5000, 8) ||
      !ledcAttach(PIN_B, 5000, 8)) {
    Serial.println("ERROR: LEDC attach failed");
    while (true) delay(1000);
  }
  render();  // Salida apagada desde el inicio.

  BLEDevice::init(DEVICE_NAME);
  BLEServer* server = BLEDevice::createServer();
  server->setCallbacks(new ConnectionCallbacks());

  BLEService* service = server->createService(SERVICE_UUID);

  BLECharacteristic* command = service->createCharacteristic(
      COMMAND_UUID, BLECharacteristic::PROPERTY_WRITE);
  command->setCallbacks(new CommandCallbacks());

  stateCharacteristic = service->createCharacteristic(
      STATE_UUID, BLECharacteristic::PROPERTY_READ |
                  BLECharacteristic::PROPERTY_NOTIFY);
  stateCharacteristic->addDescriptor(new BLE2902());
  stateCharacteristic->setValue(statePayload().c_str());

  service->start();
  BLEAdvertising* advertising = BLEDevice::getAdvertising();
  advertising->addServiceUUID(SERVICE_UUID);
  advertising->setScanResponse(true);
  BLEDevice::startAdvertising();

  Serial.println("RGB-ESP32 ready; BLE unauthenticated (laboratory)");
}

void loop() {
  if (!connected && wasConnected) {
    delay(150);
    BLEDevice::startAdvertising();
    wasConnected = false;
    Serial.println("Advertising restarted");
  }
  if (connected) wasConnected = true;
  delay(20);
}
