#pragma once
#include <cstdint>

namespace esp32core {
// Portable scheduling state used by the actual ESP32 loop and audio callbacks.
// Unsigned time differences remain correct across the millis() 32-bit rollover.
class AutonomousVoiceSchedule {
 public:
  static constexpr uint32_t kRequestLifetimeMs = 10000u;
  static constexpr uint32_t kInitialReportMs = 45000u;
  static constexpr uint32_t kReportIntervalMs = 300000u;

  // Startup honors the persisted setting without pretending a report was sent.
  void initialize(bool automaticEnabled) { automaticEnabled_ = automaticEnabled; }

  // Any subsequent OFF -> ON transition starts a fresh five-minute interval.
  void setAutomaticEnabled(bool enabled, uint32_t now) {
    if (enabled && !automaticEnabled_) {
      referenceAt_ = now;
      hasReference_ = true;
    }
    automaticEnabled_ = enabled;
  }

  bool automaticEnabled() const { return automaticEnabled_; }
  void requestReport(uint32_t now) {
    requestedAt_ = now;
    requested_ = true;
  }
  bool buttonRequested() const { return requested_; }
  bool expireButtonRequest(uint32_t now) {
    if (!requested_ ||
        static_cast<uint32_t>(now - requestedAt_) < kRequestLifetimeMs)
      return false;
    requested_ = false;
    return true;
  }
  bool periodicDue(uint32_t now) const {
    if (!automaticEnabled_) return false;
    if (!hasReference_) return now >= kInitialReportMs;
    return static_cast<uint32_t>(now - referenceAt_) >= kReportIntervalMs;
  }
  void reportStarted(uint32_t now) {
    referenceAt_ = now;
    hasReference_ = true;
    requested_ = false;
  }

 private:
  bool automaticEnabled_ = false;
  bool hasReference_ = false;
  bool requested_ = false;
  uint32_t referenceAt_ = 0;
  uint32_t requestedAt_ = 0;
};
}  // namespace esp32core
