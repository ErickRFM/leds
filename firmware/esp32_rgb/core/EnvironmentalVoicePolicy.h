#pragma once
#include <stdint.h>

namespace esp32core {

// No speech is inferred from stale or absent measurements. This policy has no
// hardware dependencies and can be exercised by the native firmware test suite.
enum class VoiceCue : uint8_t { None = 0, Hello = 1, Cold = 2, Hot = 3,
                                Dark = 4, LightRestored = 5 };
struct VoiceSample {
  bool temperatureFresh = false;
  float temperatureC = 0;
  bool lightFresh = false;
  float lightPercent = 0;
};
class EnvironmentalVoicePolicy {
 public:
  // Minimum pause between actual play requests. Avoids looping voice prompts
  // when sensors oscillate around thresholds or the player is busy.
  VoiceCue evaluate(const VoiceSample& sample, uint32_t nowMs) {
    if (sample.temperatureFresh) {
      if (sample.temperatureC >= 31.0f) hot_ = true;
      else if (sample.temperatureC <= 29.0f) hot_ = false;
      if (sample.temperatureC <= 16.0f) cold_ = true;
      else if (sample.temperatureC >= 18.0f) cold_ = false;
    }
    if (sample.lightFresh) {
      if (sample.lightPercent <= 20.0f) dark_ = true;
      else if (sample.lightPercent >= 30.0f) dark_ = false;
    }
    VoiceCue requested = VoiceCue::None;
    if (sample.temperatureFresh && hot_ && !announcedHot_) requested = VoiceCue::Hot;
    else if (sample.temperatureFresh && cold_ && !announcedCold_) requested = VoiceCue::Cold;
    else if (sample.lightFresh && dark_ && !announcedDark_) requested = VoiceCue::Dark;
    else if (sample.lightFresh && !dark_ && announcedDark_) requested = VoiceCue::LightRestored;
    if (requested == VoiceCue::None) {
      if (sample.temperatureFresh && !hot_) announcedHot_ = false;
      if (sample.temperatureFresh && !cold_) announcedCold_ = false;
      return requested;
    }
    if (hasPlayed_ && static_cast<uint32_t>(nowMs - lastPlayAt_) < 60000u)
      return VoiceCue::None;
    lastPlayAt_ = nowMs;
    hasPlayed_ = true;
    switch (requested) {
      case VoiceCue::Hot: announcedHot_ = true; break;
      case VoiceCue::Cold: announcedCold_ = true; break;
      case VoiceCue::Dark: announcedDark_ = true; break;
      case VoiceCue::LightRestored: announcedDark_ = false; break;
      default: break;
    }
    return requested;
  }
 private:
  bool hot_ = false, cold_ = false, dark_ = false;
  bool announcedHot_ = false, announcedCold_ = false, announcedDark_ = false;
  bool hasPlayed_ = false;
  uint32_t lastPlayAt_ = 0;
};
} // namespace esp32core
