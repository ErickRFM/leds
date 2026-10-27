#include <cassert>
#include <limits>
#include "../firmware/esp32_rgb/core/EnvironmentalVoicePolicy.h"
using namespace esp32core;
static VoiceSample sample(float t) {VoiceSample s{};s.temperatureFresh=true;s.temperatureC=t;return s;}
static VoiceCue announce(float temp) {
  EnvironmentalVoicePolicy p;
  auto s=sample(temp);
  assert(p.evaluate(s,3000)==VoiceCue::Hello);
  assert(p.evaluate(s,18000)==VoiceCue::None);
  s.temperatureSampleAt=1;
  assert(p.evaluate(s,18001)==VoiceCue::None);
  s.temperatureSampleAt=2;
  return p.evaluate(s,18002);
}
int main() {
  assert(announce(19)==VoiceCue::Cold);
  assert(announce(24)==VoiceCue::Comfortable);
  assert(announce(28)==VoiceCue::Warm);
  assert(announce(32)==VoiceCue::Hot);
  assert(announce(36)==VoiceCue::Extreme);
  EnvironmentalVoicePolicy p;
  auto s=sample(24);
  assert(p.evaluate(s,3000)==VoiceCue::Hello);
  assert(p.evaluate(s,18000)==VoiceCue::None);
  s.temperatureSampleAt=1;
  assert(p.evaluate(s,18001)==VoiceCue::None);
  s.temperatureSampleAt=2;
  assert(p.evaluate(s,18002)==VoiceCue::Comfortable);
  assert(p.evaluate(s,40000)==VoiceCue::None);
  s.humidityFresh=true;s.humidityPercent=98;
  assert(p.evaluate(s,41000)==VoiceCue::Humid);
  assert(p.evaluate(s,60000)==VoiceCue::None);
  s.humidityPercent=88; p.evaluate(s,61000);
  s.humidityPercent=100;
  assert(p.evaluate(s,62000)==VoiceCue::Humid);
  s.humidityFresh=false;s.lightFresh=true;s.lightPercent=15;
  assert(p.evaluate(s,80000)==VoiceCue::Dark);
  s.lightPercent=35;
  assert(p.evaluate(s,96000)==VoiceCue::LightRestored);
  s.temperatureFresh=false;s.lightFresh=false;
  assert(p.evaluate(s,112000)==VoiceCue::None);
  EnvironmentalVoicePolicy bad;
  auto invalid=sample(std::numeric_limits<float>::quiet_NaN());
  bad.evaluate(invalid,3000);
  for (unsigned i=0;i<10;i++) assert(bad.evaluate(invalid,20000+i*20000)==VoiceCue::None);
}
