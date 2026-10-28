#pragma once
#include <cstdint>

namespace esp32core {
// ESP32 button is wired between a GPIO INPUT_PULLUP and GND.
// Short press is generated on release. Long press fires only once
// after holding the button 3 seconds and never also sends short press.
class AutonomousReportButton {
 public:
  enum class Event : uint8_t { None, SpeakNow, ToggleAutomatic };
  Event update(bool pressed, uint32_t now) {
    if (pressed != raw_) {
      raw_ = pressed;
      changedAt_ = now;
    }
    if (raw_ != stable_ && static_cast<uint32_t>(now - changedAt_) >= 60u) {
      stable_ = raw_;
      if (stable_) {
        pressedAt_ = now;
        longHandled_ = false;
      } else if (!longHandled_) {
        return Event::SpeakNow;
      }
    }
    if (stable_ && raw_ && !longHandled_ &&
        static_cast<uint32_t>(now - pressedAt_) >= 3000u) {
      longHandled_ = true;
      return Event::ToggleAutomatic;
    }
    return Event::None;
  }
  // Read-only debounced state for electrical diagnostics in the main loop.
  bool isPressed() const { return stable_; }
 private:
  bool raw_ = false;
  bool stable_ = false;
  bool longHandled_ = false;
  uint32_t changedAt_ = 0;
  uint32_t pressedAt_ = 0;
};
}  // namespace esp32core
