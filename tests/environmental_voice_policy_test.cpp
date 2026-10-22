#include <cassert>
#include <limits>
#include "../firmware/esp32_rgb/core/EnvironmentalVoicePolicy.h"

int main() {
  using namespace esp32core;
  EnvironmentalVoicePolicy voice;
  VoiceSample comfortable{};
  comfortable.temperatureFresh = true;
  comfortable.temperatureC = 24.0f;
  assert(voice.evaluate(comfortable, 0u) == VoiceCue::None);
  assert(voice.evaluate(comfortable, 2999u) == VoiceCue::None);
  assert(voice.evaluate(comfortable, 3000u) == VoiceCue::Hello);
  assert(voice.evaluate(comfortable, 4000u) == VoiceCue::None);

  VoiceSample cold{};
  cold.temperatureFresh = true;
  cold.temperatureC = 14.0f;
  assert(voice.evaluate(cold, 64000u) == VoiceCue::Cold);
  assert(voice.evaluate(cold, 65000u) == VoiceCue::None);
  assert(voice.evaluate(cold, 364000u) == VoiceCue::Cold);

  VoiceSample missing{};
  assert(voice.evaluate(missing, 664000u) == VoiceCue::None);
  assert(voice.evaluate(cold, 665000u) == VoiceCue::Cold);

  EnvironmentalVoicePolicy other;
  VoiceSample hot{};
  hot.temperatureFresh = true;
  hot.temperatureC = 33.0f;
  assert(other.evaluate(hot, 3000u) == VoiceCue::Hello);
  assert(other.evaluate(hot, 63000u) == VoiceCue::Hot);
  assert(other.evaluate(hot, 363000u) == VoiceCue::Hot);

  EnvironmentalVoicePolicy invalid;
  VoiceSample bad{};
  bad.temperatureFresh = true;
  bad.temperatureC = std::numeric_limits<float>::quiet_NaN();
  bad.lightFresh = true;
  bad.lightPercent = -5.0f;
  assert(invalid.evaluate(bad, 3000u) == VoiceCue::Hello);
  assert(invalid.evaluate(bad, 63000u) == VoiceCue::None);
  bad.temperatureC = 200.0f;
  bad.lightPercent = 150.0f;
  assert(invalid.evaluate(bad, 123000u) == VoiceCue::None);
  bad.temperatureC = 14.0f;
  assert(invalid.evaluate(bad, 183000u) == VoiceCue::Cold);
  bad.temperatureC = std::numeric_limits<float>::infinity();
  assert(invalid.evaluate(bad, 483000u) == VoiceCue::None);
}
