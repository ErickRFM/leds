#pragma once

#include <cstddef>
#include <cstdio>
#include <cstring>
#include "PinManager.h"

namespace esp32core {

/** Read-only BLE telemetry: bounded string; no credentials and no pin writes. */
struct GpioTelemetryCodec {
  static constexpr uint8_t kReportedPins[] = {18, 19, 21, 22, 23, 25, 26, 27};
  static bool encode(const PinManager& pins, const bool* manualLevels,
                     char* output, size_t capacity) {
    if (!output || capacity < 8 || !manualLevels) return false;
    size_t used = 0;
    int count = std::snprintf(output, capacity, "PINS");
    if (count < 0 || static_cast<size_t>(count) >= capacity) return false;
    used = static_cast<size_t>(count);
    for (uint8_t pin : kReportedPins) {
      const PinLease* lease = pins.lease(pin);
      char state = 'F';
      if (lease) {
        if (std::strcmp(lease->owner, "led.rgb") == 0) state = 'R';
        else if (std::strcmp(lease->owner, "gpio.manual") == 0) {
          state = manualLevels[pin] ? 'H' : 'L';
        } else if (std::strcmp(lease->owner, "lab.experiment") == 0) state = 'E';
        else state = 'X';
      }
      count = std::snprintf(output + used, capacity - used, ",%u:%c",
                            static_cast<unsigned int>(pin), state);
      if (count < 0 || static_cast<size_t>(count) >= capacity - used) return false;
      used += static_cast<size_t>(count);
    }
    return true;
  }
};
}  // namespace esp32core
