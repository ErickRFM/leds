#pragma once
#include <cstdint>

namespace esp32core {
// Protects the single DFPlayer from a freshly queued ambient clip.
// A 15-second no-BUSY fallback avoids queue collisions with missing BUSY
// wiring, but a real observed BUSY low->high frees the player much sooner.
class AutonomousCueGuard {
 public:
  static constexpr uint32_t kUnknownBusyGuardMs = 15000u;
  static constexpr uint32_t kSettlingMs = 500u;
  void queued(uint32_t now) {
    pending_ = true;
    sawBusy_ = false;
    completed_ = false;
    queuedAt_ = now;
  }
  void tick(uint32_t now, bool busyPlaying) {
    if (!pending_) return;
    if (busyPlaying) {
      sawBusy_ = true;
      completed_ = false;
      return;
    }
    if (sawBusy_ && !completed_) {
      completed_ = true;
      completedAt_ = now;
    }
    if ((completed_ && static_cast<uint32_t>(now - completedAt_) >= kSettlingMs) ||
        (!sawBusy_ && static_cast<uint32_t>(now - queuedAt_) >= kUnknownBusyGuardMs)) {
      pending_ = false;
    }
  }
  bool blocks() const { return pending_; }

 private:
  bool pending_ = false;
  bool sawBusy_ = false;
  bool completed_ = false;
  uint32_t queuedAt_ = 0;
  uint32_t completedAt_ = 0;
};
}  // namespace esp32core
