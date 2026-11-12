#pragma once

#include <algorithm>
#include <cstdint>

namespace esp32core::crane {

struct AxisLimits {
  float minimumDeg = 10.0f;
  float maximumDeg = 170.0f;
  float initialDeg = 90.0f;
};

struct AxisSnapshot {
  float targetDeg = 90.0f;
  float commandedDeg = 90.0f;
  float speedDegPerSecond = 45.0f;
  bool moving = false;
};

// Host-testable motion model. It calculates commanded positions only; an SG90
// has no feedback sensor, so commandedDeg must never be presented as measured.
class ServoAxis final {
 public:
  explicit ServoAxis(AxisLimits limits = {}) : limits_(limits) {
    if (!valid(limits_)) limits_ = {};
    state_.targetDeg = state_.commandedDeg = limits_.initialDeg;
  }

  static bool valid(const AxisLimits& limits) {
    return limits.minimumDeg >= 0.0f &&
        limits.maximumDeg <= 180.0f &&
        limits.minimumDeg < limits.maximumDeg &&
        limits.initialDeg >= limits.minimumDeg &&
        limits.initialDeg <= limits.maximumDeg;
  }

  bool setTarget(float degrees, float speedDegPerSecond) {
    if (!finite(degrees) || !finite(speedDegPerSecond) ||
        speedDegPerSecond < 1.0f || speedDegPerSecond > 180.0f) return false;
    state_.targetDeg = clamp(degrees, limits_.minimumDeg, limits_.maximumDeg);
    state_.speedDegPerSecond = speedDegPerSecond;
    state_.moving = state_.targetDeg != state_.commandedDeg;
    return true;
  }

  void tick(uint32_t nowMs) {
    if (!hasTicked_) {
      lastTickMs_ = nowMs;
      hasTicked_ = true;
      return;
    }
    const uint32_t elapsed = nowMs - lastTickMs_; // wrap-safe millis arithmetic
    lastTickMs_ = nowMs;
    if (!state_.moving || elapsed == 0U) return;
    // Ignore very long scheduler stalls rather than making a large jump.
    const uint32_t boundedElapsed = elapsed > 100U ? 100U : elapsed;
    const float maxStep = state_.speedDegPerSecond *
        static_cast<float>(boundedElapsed) / 1000.0f;
    const float remaining = state_.targetDeg - state_.commandedDeg;
    if (remaining > maxStep) state_.commandedDeg += maxStep;
    else if (remaining < -maxStep) state_.commandedDeg -= maxStep;
    else state_.commandedDeg = state_.targetDeg;
    state_.moving = state_.commandedDeg != state_.targetDeg;
  }

  void stop() { state_.targetDeg = state_.commandedDeg; state_.moving = false; }
  const AxisSnapshot& snapshot() const { return state_; }
  const AxisLimits& limits() const { return limits_; }

 private:
  static bool finite(float value) {
    return value == value && value <= 1000000.0f && value >= -1000000.0f;
  }
  static float clamp(float value, float low, float high) {
    return std::max(low, std::min(high, value));
  }
  AxisLimits limits_;
  AxisSnapshot state_{};
  uint32_t lastTickMs_ = 0;
  bool hasTicked_ = false;
};

struct JoystickRaw {
  bool up = false;
  bool down = false;
  bool left = false;
  bool right = false;
  bool middle = false;
};

enum JoystickButton : uint8_t {
  JoystickUp = 1u << 0,
  JoystickDown = 1u << 1,
  JoystickLeft = 1u << 2,
  JoystickRight = 1u << 3,
  JoystickMiddle = 1u << 4
};

// Raw fields mean "electrical contact is LOW/pressed". Debouncing is performed
// in loop(), not in BLE callbacks or GPIO interrupts.
class DigitalJoystickDebouncer final {
 public:
  explicit DigitalJoystickDebouncer(uint16_t debounceMs = 25)
      : debounceMs_(debounceMs == 0 ? 1 : debounceMs) {}

  uint8_t sample(const JoystickRaw& raw, uint32_t nowMs) {
    const bool values[5] = {raw.up, raw.down, raw.left, raw.right, raw.middle};
    for (uint8_t i = 0; i < 5; ++i) {
      if (values[i] != candidate_[i]) {
        candidate_[i] = values[i];
        candidateSince_[i] = nowMs;
      } else if (stable_[i] != candidate_[i] &&
                 static_cast<uint32_t>(nowMs - candidateSince_[i]) >= debounceMs_) {
        stable_[i] = candidate_[i];
      }
    }
    uint8_t result = 0;
    for (uint8_t i = 0; i < 5; ++i) {
      if (stable_[i]) result |= static_cast<uint8_t>(1u << i);
    }
    return result;
  }

 private:
  uint16_t debounceMs_;
  bool candidate_[5] = {};
  bool stable_[5] = {};
  uint32_t candidateSince_[5] = {};
};

class CraneMotionController final {
 public:
  CraneMotionController(AxisLimits base = {}, AxisLimits arm = {})
      : base_(base), arm_(arm) {}

  bool setTargets(float baseDeg, float armDeg, float speedDegPerSecond) {
    // Validate both before changing either axis: commands are atomic.
    if (!finite(baseDeg) || !finite(armDeg) ||
        !finite(speedDegPerSecond) || speedDegPerSecond < 1.0f ||
        speedDegPerSecond > 180.0f) return false;
    return base_.setTarget(baseDeg, speedDegPerSecond) &&
        arm_.setTarget(armDeg, speedDegPerSecond);
  }

  bool setBaseTarget(float degrees, float speedDegPerSecond) {
    return base_.setTarget(degrees, speedDegPerSecond);
  }
  bool setArmTarget(float degrees, float speedDegPerSecond) {
    return arm_.setTarget(degrees, speedDegPerSecond);
  }
  void tick(uint32_t nowMs) { base_.tick(nowMs); arm_.tick(nowMs); }
  void stop() { base_.stop(); arm_.stop(); }
  const ServoAxis& base() const { return base_; }
  const ServoAxis& arm() const { return arm_; }

 private:
  static bool finite(float value) {
    return value == value && value <= 1000000.0f && value >= -1000000.0f;
  }
  ServoAxis base_;
  ServoAxis arm_;
};

}  // namespace esp32core::crane
