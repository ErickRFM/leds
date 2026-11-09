#pragma once

#include <cstddef>
#include <cstdint>
#include "PinManager.h"

namespace esp32core::crane {

constexpr uint8_t kServoBasePin = 18;
constexpr uint8_t kServoArmPin = 19;

constexpr uint8_t kJoystickUpPin = 34;
constexpr uint8_t kJoystickDownPin = 35;
constexpr uint8_t kJoystickLeftPin = 36;
constexpr uint8_t kJoystickRightPin = 39;
constexpr uint8_t kJoystickMidPin = 17;

constexpr uint8_t kReservedExistingPins[] = {
    13, 14, 16, 21, 22, 23, 25, 26, 27, 32, 33
};

constexpr uint8_t kJoystickPins[] = {
    kJoystickUpPin, kJoystickDownPin, kJoystickLeftPin,
    kJoystickRightPin, kJoystickMidPin
};

constexpr bool contains(const uint8_t* pins, size_t count, uint8_t value) {
  for (size_t i = 0; i < count; ++i) {
    if (pins[i] == value) return true;
  }
  return false;
}

constexpr bool uniqueCranePins() {
  constexpr uint8_t pins[] = {
      kServoBasePin, kServoArmPin, kJoystickUpPin, kJoystickDownPin,
      kJoystickLeftPin, kJoystickRightPin, kJoystickMidPin
  };
  for (size_t i = 0; i < sizeof(pins); ++i) {
    for (size_t j = i + 1; j < sizeof(pins); ++j) {
      if (pins[i] == pins[j]) return false;
    }
    if (contains(kReservedExistingPins,
                 sizeof(kReservedExistingPins), pins[i])) return false;
  }
  return true;
}

constexpr bool pinCapabilitiesValid() {
  if (!PinManager::canUse(kServoBasePin, PinFunction::PwmOutput) ||
      !PinManager::canUse(kServoArmPin, PinFunction::PwmOutput)) return false;
  for (uint8_t pin : kJoystickPins) {
    if (!PinManager::canUse(pin, PinFunction::DigitalInput)) return false;
  }
  return true;
}

static_assert(uniqueCranePins(), "Crane GPIO assignment collides with an existing Compi pin");
static_assert(pinCapabilitiesValid(), "Crane GPIO assignment is not supported by the ESP32 pin policy");

}  // namespace esp32core::crane
