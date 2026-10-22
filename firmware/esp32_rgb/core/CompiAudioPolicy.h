// Firmware-local copy for standalone Arduino sketch builds.
// Keep aligned with compi/audio/CompiAudioPolicy.h.
#pragma once
#include <math.h>
#include <stdint.h>

namespace CompiAudio {
enum class TempBand : uint8_t { Invalid, Cold, Comfortable, Warm, Hot, Extreme };
inline TempBand tempBand(float t) {
  if (!isfinite(t) || t < -40.f || t > 80.f) return TempBand::Invalid;
  if (t < 20.f) return TempBand::Cold;
  if (t < 26.f) return TempBand::Comfortable;
  if (t < 30.f) return TempBand::Warm;
  if (t < 35.f) return TempBand::Hot;
  return TempBand::Extreme;
}
inline uint16_t tempTrack(TempBand band) {
  switch (band) {
    case TempBand::Cold: return 1;
    case TempBand::Comfortable: return 3;
    case TempBand::Warm: return 5;
    case TempBand::Hot: return 7;
    case TempBand::Extreme: return 9;
    default: return 0;
  }
}
inline uint16_t humidityTrack(float humidity, float temperature) {
  if (!isfinite(humidity) || humidity < 0.f || humidity > 100.f || !isfinite(temperature)) return 0;
  if (humidity < 30.f) return 11;
  if (humidity <= 60.f) return 12;
  if (humidity <= 75.f) return 13;
  return temperature >= 26.f ? 14 : 13;
}
// Call with regular sensor readings; no direct UART or GPIO side effects.
// Require 3 consecutive readings in same band; cooldown is wrap-safe.
class TemperatureAnnouncement {
 public:
  uint16_t update(float celsius, uint32_t millisNow) {
    const auto next = tempBand(celsius);
    if (next == TempBand::Invalid) { pending_=next; count_=0; return 0; }
    if (next != pending_) { pending_=next; count_=1; }
    else if (count_ < 3) ++count_;
    if (count_ < 3 || next == announced_) return 0;
    if (hasAnnounced_ && uint32_t(millisNow-lastTime_) < 120000UL) return 0;
    hasAnnounced_=true; lastTime_=millisNow; announced_=next;
    return tempTrack(next);
  }
 private:
  TempBand pending_=TempBand::Invalid, announced_=TempBand::Invalid;
  uint8_t count_=0;
  uint32_t lastTime_=0;
  bool hasAnnounced_=false;
};
} // namespace CompiAudio
