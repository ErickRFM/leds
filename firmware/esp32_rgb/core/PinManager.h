#pragma once

#include <cstddef>
#include <cstdint>
#include <cstring>

namespace esp32core {

// Conservative ESP32 classic/WROOM-32 pin policy. A different board requires
// its own audited policy; unknown GPIO are DENIED rather than guessed safe.
enum class PinFunction : uint8_t { DigitalInput, DigitalOutput, PwmOutput, AnalogInput };

struct PinLease {
  const char* owner = nullptr;  // Module-owned static identifier; never a temporary String.
  PinFunction function = PinFunction::DigitalInput;
};

class PinManager final {
 public:
  static constexpr uint8_t kPinCount = 40;

  static constexpr bool outputCapable(uint8_t pin) {
    switch (pin) {
      case 16: case 17: case 18: case 19:
      case 21: case 22: case 23:
      case 25: case 26: case 27:
      case 32: case 33: return true;
      default: return false;
    }
  }

  static constexpr bool analogInputCapable(uint8_t pin) {
    // ADC1 only: ADC2 is unavailable/restricted while Wi-Fi is active.
    switch (pin) {
      case 32: case 33: case 34: case 35: case 36: case 39: return true;
      default: return false;
    }
  }

  static constexpr bool canUse(uint8_t pin, PinFunction function) {
    if (pin >= kPinCount) return false;
    switch (function) {
      case PinFunction::DigitalInput:
        return outputCapable(pin) || analogInputCapable(pin);
      case PinFunction::DigitalOutput:
      case PinFunction::PwmOutput:
        return outputCapable(pin);
      case PinFunction::AnalogInput:
        return analogInputCapable(pin);
    }
    return false;
  }

  // Pins owned by a fixed system module (for example the RGB engine). A
  // reserved pin can only ever be acquired by its registered owner, so a
  // sensor cannot silently steal GPIO 25/26/27 from the RGB module.
  static constexpr bool reservedForRgb(uint8_t pin) {
    return pin == 25 || pin == 26 || pin == 27;
  }

  bool registerSystemPin(uint8_t pin, const char* owner) {
    if (pin >= kPinCount || !owner || !owner[0] || reserved_[pin]) return false;
    reserved_[pin] = owner;
    return true;
  }

  const char* systemOwner(uint8_t pin) const {
    return pin < kPinCount ? reserved_[pin] : nullptr;
  }

  bool isSystemReserved(uint8_t pin) const {
    return pin < kPinCount && reserved_[pin] != nullptr;
  }

  bool acquire(uint8_t pin, const char* owner, PinFunction function) {
    if (!canUse(pin, function) || !owner || !owner[0] || leases_[pin].owner) return false;
    if (reserved_[pin] && std::strcmp(reserved_[pin], owner) != 0) return false;
    leases_[pin] = {owner, function};
    return true;
  }

  bool release(uint8_t pin, const char* owner) {
    if (pin >= kPinCount || !owner || !leases_[pin].owner ||
        std::strcmp(leases_[pin].owner, owner) != 0) return false;
    leases_[pin] = {};
    return true;
  }

  bool belongsTo(uint8_t pin, const char* owner) const {
    return pin < kPinCount && owner && leases_[pin].owner &&
        std::strcmp(leases_[pin].owner, owner) == 0;
  }

  const PinLease* lease(uint8_t pin) const {
    return pin < kPinCount && leases_[pin].owner ? &leases_[pin] : nullptr;
  }

  size_t leasedCount() const {
    size_t count = 0;
    for (const auto& lease : leases_) if (lease.owner) ++count;
    return count;
  }

 private:
  PinLease leases_[kPinCount] = {};
  const char* reserved_[kPinCount] = {};
};
}  // namespace esp32core
