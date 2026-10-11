#pragma once
#include <Arduino.h>
#include "PinManager.h"

namespace esp32core {

// Declarative, bounded "blink" experiment. It never executes uploaded code.
// GPIO is returned to a safe LOW and lease released on stop or completion.
class ExperimentRuntime final {
 public:
  bool start(PinManager& pins, uint8_t pin, uint16_t intervalMs,
             uint8_t cycles, uint32_t nowMs) {
    if (active_ || !PinManager::canUse(pin, PinFunction::DigitalOutput) ||
        intervalMs < 100 || intervalMs > 2000 || cycles < 1 || cycles > 20 ||
        !pins.acquire(pin, kOwner, PinFunction::DigitalOutput)) return false;
    pin_ = pin;
    intervalMs_ = intervalMs;
    transitionsLeft_ = static_cast<uint8_t>(cycles * 2);
    lastMs_ = nowMs;
    high_ = true;
    active_ = true;
    pinMode(pin_, OUTPUT);
    digitalWrite(pin_, HIGH);
    return true;
  }

  void tick(PinManager& pins, uint32_t nowMs) {
    if (!active_ || static_cast<uint32_t>(nowMs - lastMs_) < intervalMs_) return;
    lastMs_ = nowMs;
    if (transitionsLeft_ > 0) --transitionsLeft_;
    if (transitionsLeft_ == 0) { stop(pins); return; }
    high_ = !high_;
    digitalWrite(pin_, high_ ? HIGH : LOW);
  }

  void stop(PinManager& pins) {
    if (!active_) return;
    digitalWrite(pin_, LOW);
    pinMode(pin_, INPUT);
    pins.release(pin_, kOwner);
    active_ = false;
    high_ = false;
    transitionsLeft_ = 0;
  }

  bool active() const { return active_; }
  int currentPin() const { return active_ ? pin_ : -1; }

 private:
  static constexpr const char* kOwner = "lab.experiment";
  uint8_t pin_ = 0;
  uint8_t transitionsLeft_ = 0;
  uint16_t intervalMs_ = 100;
  uint32_t lastMs_ = 0;
  bool high_ = false;
  bool active_ = false;
};

} // namespace esp32core
