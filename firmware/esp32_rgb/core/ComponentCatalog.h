#pragma once

#include <cstddef>
#include <cstdio>
#include "ComponentContract.h"

namespace esp32core {

// Declarative component catalog shared by firmware and the Android client.
// A catalog entry is pure metadata: it never implies the component is present.
// Presence must be proven by a successful begin()/probe on the device.
using ComponentCapability = uint16_t;
constexpr uint16_t kCapHumidity = 1u << 0;
constexpr uint16_t kCapTemperature = 1u << 1;
constexpr uint16_t kCapSoilMoisture = 1u << 2;
constexpr uint16_t kCapRgb = 1u << 3;
constexpr uint16_t kCapDisplay = 1u << 4;
constexpr uint16_t kCapAmbientLight = 1u << 5;

struct ComponentCatalogEntry {
  const char* id;
  const char* name;
  ComponentType type;
  HardwareInterface primaryInterface;
  uint8_t defaultPin;
  uint16_t capabilities;
  bool supported;
};

inline constexpr ComponentCatalogEntry kCatalog[] = {
    {"dht11", "DHT11", ComponentType::Sensor, HardwareInterface::Digital, 16,
     kCapHumidity | kCapTemperature, true},
    {"dht22", "DHT22", ComponentType::Sensor, HardwareInterface::Digital, 32,
     kCapHumidity | kCapTemperature, true},
    {"soil_moisture", "Soil moisture", ComponentType::Sensor, HardwareInterface::Adc, 34,
     kCapSoilMoisture, true},
    {"ldr_light", "LDR Light", ComponentType::Sensor, HardwareInterface::Adc, 36,
     kCapAmbientLight, true},
    {"led_rgb", "RGB LED", ComponentType::Actuator, HardwareInterface::Pwm, 27,
     kCapRgb, true},
    {"ssd1306_oled", "SSD1306 OLED", ComponentType::Display, HardwareInterface::I2c, 0x3C,
     kCapDisplay, true},
    {"audio_dfplayer", "DFPlayer Mini", ComponentType::Actuator, HardwareInterface::Uart, 17,
     0, true},
};

inline constexpr size_t kCatalogSize = sizeof(kCatalog) / sizeof(kCatalog[0]);

// Serializes the catalog as bounded JSON. Returns false when it does not fit.
inline bool encodeCatalog(char* output, size_t capacity) {
  if (!output || capacity < 32) return false;
  size_t used = 0;
  int written = std::snprintf(output, capacity, "{\"version\":1,\"components\":[");
  if (written < 0 || static_cast<size_t>(written) >= capacity) return false;
  used = static_cast<size_t>(written);
  for (size_t i = 0; i < kCatalogSize; ++i) {
    const ComponentCatalogEntry& entry = kCatalog[i];
    written = std::snprintf(
        output + used, capacity - used,
        "%s{\"id\":\"%s\",\"name\":\"%s\",\"type\":\"%s\",\"interface\":\"%s\","
        "\"supported\":%s}",
        i == 0 ? "" : ",", entry.id, entry.name, componentTypeName(entry.type),
        interfaceName(entry.primaryInterface), entry.supported ? "true" : "false");
    if (written < 0 || static_cast<size_t>(written) >= capacity - used) return false;
    used += static_cast<size_t>(written);
  }
  written = std::snprintf(output + used, capacity - used, "]}");
  if (written < 0 || static_cast<size_t>(written) >= capacity - used) return false;
  return true;
}
}  // namespace esp32core
