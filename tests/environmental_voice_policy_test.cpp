#include <cassert>
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
}
