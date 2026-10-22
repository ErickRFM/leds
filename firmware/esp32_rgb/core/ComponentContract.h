#pragma once

#include <cstddef>
#include <cstdint>

namespace esp32core {

// Declarative hardware component contract shared by firmware and the Android
// catalog. A descriptor is pure metadata: it never implies the component is
// present or working. Presence must be proven by a successful begin()/probe.
enum class ComponentType : uint8_t { Sensor, Actuator, Display, Controller };

enum class HardwareInterface : uint8_t {
  Gpio,
  Adc,
  I2c,
  Spi,
  Pwm,
  OneWire,
  Digital,
};

// Lifecycle states. Catalog -> Configured -> Active is the only forward path;
// Detected is reserved for evidence gathered from the bus/firmware, never for a
// user merely adding a widget.
enum class ComponentState : uint8_t {
  Catalog,
  Configured,
  Detected,
  Active,
  Error,
  Disconnected,
  Unsupported,
};

constexpr const char* componentTypeName(ComponentType type) {
  switch (type) {
    case ComponentType::Sensor: return "sensor";
    case ComponentType::Actuator: return "actuator";
    case ComponentType::Display: return "display";
    case ComponentType::Controller: return "controller";
  }
  return "unknown";
}

constexpr const char* componentStateName(ComponentState state) {
  switch (state) {
    case ComponentState::Catalog: return "catalog";
    case ComponentState::Configured: return "configured";
    case ComponentState::Detected: return "detected";
    case ComponentState::Active: return "active";
    case ComponentState::Error: return "error";
    case ComponentState::Disconnected: return "disconnected";
    case ComponentState::Unsupported: return "unsupported";
  }
  return "unknown";
}

constexpr const char* interfaceName(HardwareInterface iface) {
  switch (iface) {
    case HardwareInterface::Gpio: return "gpio";
    case HardwareInterface::Adc: return "adc";
    case HardwareInterface::I2c: return "i2c";
    case HardwareInterface::Spi: return "spi";
    case HardwareInterface::Pwm: return "pwm";
    case HardwareInterface::OneWire: return "one_wire";
    case HardwareInterface::Digital: return "digital";
  }
  return "unknown";
}
}  // namespace esp32core
