#pragma once

#include <Arduino.h>
#include "../core/ModuleRegistry.h"
#include "../core/CranePinMap.h"
#include "../core/CraneMotionCore.h"
#include "../core/CraneProtocol.h"

namespace esp32core {

// Optional local-first robotics module. Remote commands are invoked only by
// the main loop after the existing BLE owner-session authorization gate.
class CraneRoboticsModule final : public IModule {
 public:
  const char* id() const override { return "robotics.crane.2axis"; }
  uint16_t version() const override { return 1; }

  bool begin(PinManager& manager) override {
    if (ready_) return false;
    PinManager* acquired = &manager;
    for (size_t i = 0; i < 2; ++i) {
      if (!acquired->acquire(pins_[i], id(), PinFunction::PwmOutput)) {
        releaseClaims(*acquired);
        return false;
      }
    }
    for (size_t i = 2; i < 7; ++i) {
      if (!acquired->acquire(pins_[i], id(), PinFunction::DigitalInput)) {
        releaseClaims(*acquired);
        return false;
      }
    }

    // GPIO34/35/36/39 have no internal pull-ups. All five contacts require
    // external pull-ups to 3.3 V and the joystick COM pin connected to GND.
    for (size_t i = 2; i < 7; ++i) pinMode(pins_[i], INPUT);

    if (!ledcAttach(pins_[0], kPwmFrequencyHz, kPwmResolutionBits)) {
      releaseClaims(*acquired);
      return false;
    }
    baseAttached_ = true;
    if (!ledcAttach(pins_[1], kPwmFrequencyHz, kPwmResolutionBits)) {
      ledcDetach(pins_[0]);
      baseAttached_ = false;
      pinMode(pins_[0], INPUT);
      releaseClaims(*acquired);
      return false;
    }
    armAttached_ = true;
    motion_.tick(millis());
    writeAxis(pins_[0], motion_.base().snapshot().commandedDeg);
    writeAxis(pins_[1], motion_.arm().snapshot().commandedDeg);
    lastBaseWritten_ = motion_.base().snapshot().commandedDeg;
    lastArmWritten_ = motion_.arm().snapshot().commandedDeg;
    lastRepeatAt_ = millis();
    ready_ = true;
    return true;
  }

  void tick(uint32_t nowMs) override {
    if (!ready_) return;
    if (remoteMode_ && static_cast<uint32_t>(nowMs - lastRemoteCommandAt_) > kRemoteLeaseMs) {
      motion_.stop();
      remoteMode_ = false;
      localRearmRequired_ = true;
    }
    const crane::JoystickRaw raw{
        digitalRead(pins_[2]) == LOW,
        digitalRead(pins_[3]) == LOW,
        digitalRead(pins_[4]) == LOW,
        digitalRead(pins_[5]) == LOW,
        digitalRead(pins_[6]) == LOW
    };
    const uint8_t buttons = joystick_.sample(raw, nowMs);
    const bool middle = (buttons & crane::JoystickMiddle) != 0;
    if (!remoteMode_ && !localRearmRequired_ && middle && !middleWasPressed_) {
      motion_.setTargets(90.0f, 90.0f, kDefaultSpeedDegPerSecond);
    }
    middleWasPressed_ = middle;

    if (!remoteMode_ && !localRearmRequired_ &&
        static_cast<uint32_t>(nowMs - lastRepeatAt_) >= kRepeatIntervalMs) {
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

    if (localRearmRequired_ && buttons == 0) localRearmRequired_ = false;
    motion_.tick(nowMs);
    const float baseNow = motion_.base().snapshot().commandedDeg;
    const float armNow = motion_.arm().snapshot().commandedDeg;
    if (baseNow != lastBaseWritten_) {
      writeAxis(pins_[0], baseNow);
      lastBaseWritten_ = baseNow;
    }
    if (armNow != lastArmWritten_) {
      writeAxis(pins_[1], armNow);
      lastArmWritten_ = armNow;
    }
  }

  void stop(PinManager& manager) override {
    if (!ready_) return;
    motion_.stop();
    if (baseAttached_) ledcDetach(pins_[0]);
    if (armAttached_) ledcDetach(pins_[1]);
    baseAttached_ = armAttached_ = false;
    pinMode(pins_[0], INPUT);
    pinMode(pins_[1], INPUT);
    releaseClaims(manager);
    ready_ = false;
  }

  // Rewires all seven signals. Only legal while the module is stopped:
  // a running PWM axis or live joystick must never jump pads mid-flight.
  // `pins` holds [base, arm, up, down, left, right, mid]. Fails closed on
  // duplicates, unknown pads, RGB-reserved pads, output pads on input-only
  // silicon, or input pads that cannot be read.
  bool configurePins(const uint8_t* pins) {
    if (ready_ || pins == nullptr) return false;
    for (size_t i = 0; i < 7; ++i) {
      if (pins[i] >= PinManager::kPinCount) return false;
      for (size_t j = i + 1; j < 7; ++j) {
        if (pins[i] == pins[j]) return false;
      }
      if (PinManager::reservedForRgb(pins[i])) return false;
      if (i < 2) {
        if (!PinManager::outputCapable(pins[i])) return false;
      } else if (!PinManager::canUse(pins[i], PinFunction::DigitalInput)) {
        return false;
      }
    }
    for (size_t i = 0; i < 7; ++i) pins_[i] = pins[i];
    return true;
  }

  uint8_t pin(size_t index) const {
    return index < 7 ? pins_[index] : 0;
  }

  bool setRemoteMode(crane::ControlMode mode, uint32_t nowMs) {
    if (!ready_) return false;
    remoteMode_ = mode == crane::ControlMode::Remote;
    lastRemoteCommandAt_ = nowMs;
    if (!remoteMode_) motion_.stop();
    else localRearmRequired_ = true;
    return true;
  }

  bool applyRemoteTargets(float baseDeg, float armDeg,
                          float speedDegPerSecond, uint32_t nowMs) {
    if (!ready_ || !motion_.setTargets(baseDeg, armDeg, speedDegPerSecond)) return false;
    remoteMode_ = true;
    localRearmRequired_ = true;
    lastRemoteCommandAt_ = nowMs;
    return true;
  }

  bool applyRemoteAxis(bool baseAxis, float angleDeg,
                       float speedDegPerSecond, uint32_t nowMs) {
    if (!ready_) return false;
    const bool accepted = baseAxis
        ? motion_.setBaseTarget(angleDeg, speedDegPerSecond)
        : motion_.setArmTarget(angleDeg, speedDegPerSecond);
    if (!accepted) return false;
    remoteMode_ = true;
    localRearmRequired_ = true;
    lastRemoteCommandAt_ = nowMs;
    return true;
  }

  bool touchRemoteLease(uint32_t nowMs) {
    if (!ready_ || !remoteMode_) return false;
    lastRemoteCommandAt_ = nowMs;
    return true;
  }

  bool homeRemote(uint32_t nowMs) {
    return applyRemoteTargets(90.0f, 90.0f, kDefaultSpeedDegPerSecond, nowMs);
  }

  bool stopRemote(uint32_t nowMs) {
    if (!ready_) return false;
    motion_.stop();
    remoteMode_ = true;
    localRearmRequired_ = true;
    lastRemoteCommandAt_ = nowMs;
    return true;
  }

  crane::ControlMode controlMode() const {
    return remoteMode_ ? crane::ControlMode::Remote : crane::ControlMode::Local;
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
  static constexpr uint32_t kRemoteLeaseMs = 1500;
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
    for (size_t i = 0; i < 7; ++i) manager.release(pins_[i], id());
  }

  // Currently configured signals; the audited compile-time map until an
  // authorized runtime rewire replaces them. Valid even while stopped.
  uint8_t pins_[7] = {
      crane::kServoBasePin, crane::kServoArmPin,
      crane::kJoystickUpPin, crane::kJoystickDownPin,
      crane::kJoystickLeftPin, crane::kJoystickRightPin,
      crane::kJoystickMidPin};
  crane::CraneMotionController motion_{};
  crane::DigitalJoystickDebouncer joystick_{};
  float lastBaseWritten_ = -1.0f;
  float lastArmWritten_ = -1.0f;
  uint32_t lastRepeatAt_ = 0;
  bool middleWasPressed_ = false;
  bool remoteMode_ = false;
  bool localRearmRequired_ = false;
  uint32_t lastRemoteCommandAt_ = 0;
  bool baseAttached_ = false;
  bool armAttached_ = false;
  bool ready_ = false;
};

}  // namespace esp32core
