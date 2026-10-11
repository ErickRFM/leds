#pragma once
#include <Arduino.h>
#include <cstddef>
#include "PinManager.h"

namespace esp32core {

// Bounded declarative GPIO sequencer. No uploaded C++/scripts or shared-heap
// execution. One approved pin per experiment, max 16 user steps / 20 seconds.
class ExperimentRuntime final {
 public:
  struct Step {
    bool high;
    uint16_t durationMs;
  };
  static constexpr size_t kMaxSequenceSteps = 16;
  static constexpr uint32_t kMaxDurationMs = 20000;

  bool startSequence(PinManager& pins, uint8_t pin, const Step* steps,
                     size_t count, uint32_t nowMs) {
    if (count == 0 || count > kMaxSequenceSteps) return false;
    return startSteps(pins, pin, steps, count, nowMs);
  }

  // Compatibility: original blink contract, now also obeys 20s safety budget.
  bool start(PinManager& pins, uint8_t pin, uint16_t intervalMs,
             uint8_t cycles, uint32_t nowMs) {
    if (cycles == 0 || cycles > 20 || intervalMs < 100 || intervalMs > 2000 ||
        static_cast<uint32_t>(cycles) * 2U * intervalMs > kMaxDurationMs) return false;
    Step steps[kMaxStoredSteps] = {};
    for (size_t i = 0; i < cycles * 2U; ++i) {
      steps[i] = {(i % 2) == 0, intervalMs};
    }
    return startSteps(pins, pin, steps, cycles * 2U, nowMs);
  }

  void tick(PinManager& pins, uint32_t nowMs) {
    if (!active_ || static_cast<uint32_t>(nowMs - startedAtMs_) >= kMaxDurationMs) {
      if (active_) stop(pins);
      return;
    }
    if (static_cast<uint32_t>(nowMs - stepAtMs_) < steps_[stepIndex_].durationMs) return;
    ++stepIndex_;
    if (stepIndex_ >= stepCount_) { stop(pins); return; }
    stepAtMs_ = nowMs;
    digitalWrite(pin_, steps_[stepIndex_].high ? HIGH : LOW);
  }

  void stop(PinManager& pins) {
    if (!active_) return;
    digitalWrite(pin_, LOW);
    pinMode(pin_, INPUT);
    pins.release(pin_, kOwner);
    active_ = false;
    stepCount_ = 0;
    stepIndex_ = 0;
  }

  bool active() const { return active_; }
  int currentPin() const { return active_ ? pin_ : -1; }
  size_t currentStep() const { return active_ ? stepIndex_ : 0; }

 private:
  static constexpr const char* kOwner = "lab.experiment";
  static constexpr size_t kMaxStoredSteps = 40; // 20 blink cycles
  Step steps_[kMaxStoredSteps] = {};
  uint8_t pin_ = 0;
  size_t stepCount_ = 0;
  size_t stepIndex_ = 0;
  uint32_t startedAtMs_ = 0;
  uint32_t stepAtMs_ = 0;
  bool active_ = false;

  bool startSteps(PinManager& pins, uint8_t pin, const Step* steps,
                  size_t count, uint32_t nowMs) {
    if (active_ || !steps || count == 0 || count > kMaxStoredSteps ||
        !PinManager::canUse(pin, PinFunction::DigitalOutput)) return false;
    uint32_t total = 0;
    for (size_t i = 0; i < count; ++i) {
      if (steps[i].durationMs < 100 || steps[i].durationMs > 2000) return false;
      total += steps[i].durationMs;
      if (total > kMaxDurationMs) return false;
    }
    if (!pins.acquire(pin, kOwner, PinFunction::DigitalOutput)) return false;
    for (size_t i = 0; i < count; ++i) steps_[i] = steps[i];
    pin_ = pin;
    stepCount_ = count;
    stepIndex_ = 0;
    startedAtMs_ = nowMs;
    stepAtMs_ = nowMs;
    active_ = true;
    pinMode(pin_, OUTPUT);
    digitalWrite(pin_, steps_[0].high ? HIGH : LOW);
    return true;
  }
};
} // namespace esp32core
