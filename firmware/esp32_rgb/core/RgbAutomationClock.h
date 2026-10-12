#pragma once
#include <cstdint>
namespace esp32core {
class RgbAutomationClock final {
 public:
  enum class Result : uint8_t { None, TurnOn, TurnOff };
  static constexpr uint32_t kMinIntervalMs = 1000;
  static constexpr uint32_t kMaxIntervalMs = 86400000;
  bool configure(uint32_t intervalMs, uint32_t nowMs, bool startOn) {
    if (intervalMs < kMinIntervalMs || intervalMs > kMaxIntervalMs) return false;
    intervalMs_ = intervalMs; anchorMs_ = nowMs; nextOn_ = startOn; active_ = true;
    advancedIntervals_ = 0;
    return true;
  }
  Result tick(uint32_t nowMs) {
    advancedIntervals_ = 0;
    if (!active_) return Result::None;
    const uint32_t elapsed = static_cast<uint32_t>(nowMs - anchorMs_);
    if (elapsed < intervalMs_) return Result::None;
    const uint32_t intervals = elapsed / intervalMs_;
    advancedIntervals_ = intervals;
    anchorMs_ += intervals * intervalMs_;
    const bool actionOn = (intervals % 2u) == 1u ? nextOn_ : !nextOn_;
    if ((intervals % 2u) == 1u) nextOn_ = !nextOn_;
    return actionOn ? Result::TurnOn : Result::TurnOff;
  }
  void stop() { active_ = false; advancedIntervals_ = 0; }
  bool active() const { return active_; }
  // Number of logical intervals elapsed since the previous tick. Even if a
  // busy network task delayed the loop, execution limits count real time.
  uint32_t advancedIntervals() const { return advancedIntervals_; }
  uint32_t intervalMs() const { return intervalMs_; }
 private:
  uint32_t intervalMs_ = 0;
  uint32_t anchorMs_ = 0;
  bool nextOn_ = true;
  bool active_ = false;
  uint32_t advancedIntervals_ = 0;
};
} // namespace esp32core
