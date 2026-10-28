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
  // RGB and voice share half-degree temperature transitions.
  EnvironmentalVoicePolicy hysteresis;
  auto h=sample(25.0f);
  hysteresis.evaluate(h,3000u);
  hysteresis.evaluate(h,19000u);
  h.temperatureSampleAt=1000u;
  hysteresis.evaluate(h,19001u);
  h.temperatureSampleAt=2000u;
  assert(hysteresis.evaluate(h,19002u)==VoiceCue::Comfortable);
  h.temperatureC=26.2f;
  h.temperatureSampleAt=3000u;
  assert(hysteresis.evaluate(h,35000u)==VoiceCue::None);
  h.temperatureC=26.7f;
  h.temperatureSampleAt=4000u;
  assert(hysteresis.evaluate(h,52000u)==VoiceCue::None);
  h.temperatureSampleAt=5000u;
  assert(hysteresis.evaluate(h,53000u)==VoiceCue::None);
  h.temperatureSampleAt=6000u;
  assert(hysteresis.evaluate(h,54000u)==VoiceCue::Warm);
  // A valid zero-percent light reading takes priority when both the
  // temperature and darkness alerts become due. Restoration uses 30%.
  EnvironmentalVoicePolicy lightPriority;
  auto lp=sample(24);
  lp.lightFresh=true; lp.lightPercent=0;
  assert(lightPriority.evaluate(lp,3000u)==VoiceCue::Hello);
  lp.temperatureSampleAt=1;
  assert(lightPriority.evaluate(lp,18000u)==VoiceCue::Dark);
  lp.temperatureSampleAt=2;
  assert(lightPriority.evaluate(lp,34000u)==VoiceCue::None);
  lp.temperatureSampleAt=3;
  assert(lightPriority.evaluate(lp,34001u)==VoiceCue::Comfortable);
  lp.lightPercent=25;
  assert(lightPriority.evaluate(lp,50000u)==VoiceCue::None);
  lp.lightPercent=30;
  assert(lightPriority.evaluate(lp,66000u)==VoiceCue::LightRestored);
  lp.lightFresh=false; lp.lightPercent=0;
  assert(lightPriority.evaluate(lp,82000u)==VoiceCue::None);
  EnvironmentalVoicePolicy bad;
  auto invalid=sample(std::numeric_limits<float>::quiet_NaN());
  bad.evaluate(invalid,3000);
  for (unsigned i=0;i<10;i++) assert(bad.evaluate(invalid,20000+i*20000)==VoiceCue::None);
}
