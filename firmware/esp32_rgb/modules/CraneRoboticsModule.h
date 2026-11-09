#pragma once

#include <Arduino.h>
#include "../core/ModuleRegistry.h"
#include "../core/CranePinMap.h"
#include "../core/CraneMotionCore.h"

namespace esp32core {

// Optional local-first robotics module. Remote commands are deliberately not
// accepted here yet; they will be added only through the authenticated BLE
// owner-session path after protocol tests are in place.
class CraneRoboticsModule final : public IModule {
 public:
  const char* id() const override { return "robotics.crane.2axis"; }
  uint16_t version() const override { return 1; }

  bool begin(PinManager& manager) override {
    if (ready_) return false;
    PinManager* acquired = &manager;
    size_t claimed = 0;
    const uint8_t outputs[] = {crane::kServoBasePin, crane::kServoArmPin};
    const uint8_t inputs[] = {
        crane::kJoystickUpPin, crane::kJoystickDownPin,
        crane::kJoystickLeftPin, crane::kJoystickRightPin,
        crane::kJoystickMidPin
    };
    for (uint8_t pin : outputs) {
      if (!acquired->acquire(pin, id(), PinFunction::PwmOutput)) {
        releaseClaims(*acquired);
        return false;
      }
      ++claimed;
    }
    for (uint8_t pin : inputs) {
      if (!acquired->acquire(pin, id(), PinFunction::DigitalInput)) {
        releaseClaims(*acquired);
        return false;
      }
      ++claimed;
    }

    // GPIO34/35/36/39 have no internal pull-ups. All five contacts require
    // external pull-ups to 3.3 V and the joystick COM pin connected to GND.
    for (uint8_t pin : inputs) pinMode(pin, INPUT);

    if (!ledcAttach(outputs[0], kPwmFrequencyHz, kPwmResolutionBits)) {
      releaseClaims(*acquired);
      return false;
    }
    baseAttached_ = true;
    if (!ledcAttach(outputs[1], kPwmFrequencyHz, kPwmResolutionBits)) {
      ledcDetach(outputs[0]);
      baseAttached_ = false;
      pinMode(outputs[0], INPUT);
      releaseClaims(*acquired);
      return false;
    }
    armAttached_ = true;
    pins_ = acquired;
    motion_.tick(millis());
    writeAxis(outputs[0], motion_.base().snapshot().commandedDeg);
    writeAxis(outputs[1], motion_.arm().snapshot().commandedDeg);
    lastBaseWritten_ = motion_.base().snapshot().commandedDeg;
    lastArmWritten_ = motion_.arm().snapshot().commandedDeg;
    lastRepeatAt_ = millis();
    ready_ = true;
    return true;
  }

  void tick(uint32_t nowMs) override {
    if (!ready_) return;
    const crane::JoystickRaw raw{
        digitalRead(crane::kJoystickUpPin) == LOW,
        digitalRead(crane::kJoystickDownPin) == LOW,
        digitalRead(crane::kJoystickLeftPin) == LOW,
        digitalRead(crane::kJoystickRightPin) == LOW,
        digitalRead(crane::kJoystickMidPin) == LOW
    };
    const uint8_t buttons = joystick_.sample(raw, nowMs);
    const bool middle = (buttons & crane::JoystickMiddle) != 0;
    if (middle && !middleWasPressed_) {
      motion_.setTargets(90.0f, 90.0f, kDefaultSpeedDegPerSecond);
    }
    middleWasPressed_ = middle;

    if (static_cast<uint32_t>(nowMs - lastRepeatAt_) >= kRepeatIntervalMs) {
      lastRepeatAt_ = nowMs;
      const bool up = (buttons & crane::JoystickUp) != 0;
      const bool down = (buttons & crane::JoystickDown) != 0;
      const bool left = (buttons & crane::JoystickLeft) != 0;
      const bool right = (buttons & crane::JoystickRight) != 0;
      const auto& base = motion_.base().snapshot();
      const auto& arm = motion_.arm().snapshot();
      if (left != right) {
        motion_.setBaseTarget(base.targetDeg + (right ? 1.0f : -1.0f),
                              kDefaultSpeedDegPerSecond);
      }
      if (up != down) {
        motion_.setArmTarget(arm.targetDeg + (up ? 1.0f : -1.0f),
                             kDefaultSpeedDegPerSecond);
      }
    }

    motion_.tick(nowMs);
    const float baseNow = motion_.base().snapshot().commandedDeg;
    const float armNow = motion_.arm().snapshot().commandedDeg;
    if (baseNow != lastBaseWritten_) {
      writeAxis(crane::kServoBasePin, baseNow);
      lastBaseWritten_ = baseNow;
    }
    if (armNow != lastArmWritten_) {
      writeAxis(crane::kServoArmPin, armNow);
      lastArmWritten_ = armNow;
    }
  }

  void stop(PinManager& manager) override {
    if (!ready_) return;
    motion_.stop();
    if (baseAttached_) ledcDetach(crane::kServoBasePin);
    if (armAttached_) ledcDetach(crane::kServoArmPin);
    baseAttached_ = armAttached_ = false;
    pinMode(crane::kServoBasePin, INPUT);
    pinMode(crane::kServoArmPin, INPUT);
    releaseClaims(manager);
    pins_ = nullptr;
    ready_ = false;
  }

  bool ready() const { return ready_; }
  const crane::CraneMotionController& motion() const { return motion_; }

 private:
  static constexpr uint32_t kPwmFrequencyHz = 50;
  static constexpr uint8_t kPwmResolutionBits = 16;
  static constexpr uint16_t kPwmPeriodUs = 20000;
  static constexpr uint16_t kMinPulseUs = 1000;
  static constexpr uint16_t kMaxPulseUs = 2000;
  static constexpr uint32_t kRepeatIntervalMs = 35;
  static constexpr float kDefaultSpeedDegPerSecond = 60.0f;

  static void writeAxis(uint8_t pin, float degrees) {
    const float bounded = degrees < 0.0f ? 0.0f :
        (degrees > 180.0f ? 180.0f : degrees);
    const float pulse = static_cast<float>(kMinPulseUs) +
        (static_cast<float>(kMaxPulseUs - kMinPulseUs) * bounded / 180.0f);
    const uint32_t duty = static_cast<uint32_t>(
        pulse * 65535.0f / static_cast<float>(kPwmPeriodUs));
    ledcWrite(pin, duty);
  }

  void releaseClaims(PinManager& manager) {
    manager.release(crane::kServoBasePin, id());
    manager.release(crane::kServoArmPin, id());
    manager.release(crane::kJoystickUpPin, id());
    manager.release(crane::kJoystickDownPin, id());
    manager.release(crane::kJoystickLeftPin, id());
    manager.release(crane::kJoystickRightPin, id());
    manager.release(crane::kJoystickMidPin, id());
  }

  PinManager* pins_ = nullptr;
  crane::CraneMotionController motion_{};
  crane::DigitalJoystickDebouncer joystick_{};
  float lastBaseWritten_ = -1.0f;
  float lastArmWritten_ = -1.0f;
  uint32_t lastRepeatAt_ = 0;
  bool middleWasPressed_ = false;
  bool baseAttached_ = false;
  bool armAttached_ = false;
  bool ready_ = false;
};

}  // namespace esp32core
