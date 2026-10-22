#include <cassert>
#include <cstdint>
#include <iostream>
#include "../firmware/esp32_rgb/core/EnvironmentalVoicePolicy.h"
using namespace esp32core;
int main() {
  EnvironmentalVoicePolicy policy;
  VoiceSample sample{};
  assert(policy.evaluate(sample, 0) == VoiceCue::None);
  assert(policy.evaluate(sample, 2999) == VoiceCue::None);
  assert(policy.evaluate(sample, 3000) == VoiceCue::Hello);
  assert(policy.evaluate(sample, 3001) == VoiceCue::None);
  sample.temperatureFresh = true;
  sample.temperatureC = 33.0f;
  assert(policy.evaluate(sample, 63000) == VoiceCue::Hot);
  assert(policy.evaluate(sample, 122999) == VoiceCue::None);
  assert(policy.evaluate(sample, 362999) == VoiceCue::None);
  assert(policy.evaluate(sample, 363000) == VoiceCue::Hot);
  sample.temperatureFresh = false;
  assert(policy.evaluate(sample, 663000) == VoiceCue::None);
  sample.temperatureFresh = true;
  sample.temperatureC = 25.0f;
  assert(policy.evaluate(sample, 663001) == VoiceCue::None);
  sample.temperatureC = 15.0f;
  assert(policy.evaluate(sample, 663002) == VoiceCue::Cold);
  assert(policy.evaluate(sample, 963001) == VoiceCue::None);
  assert(policy.evaluate(sample, 963002) == VoiceCue::Cold);
  std::cout << "environmental voice cycle: ALL OK\n";
  return 0;
}
