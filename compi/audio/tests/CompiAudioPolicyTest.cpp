#include "../CompiAudioPolicy.h"
#include <assert.h>
#include <limits>
int main() {
  using namespace CompiAudio;
  assert(tempBand(19.9f)==TempBand::Cold);
  assert(tempBand(20.f)==TempBand::Comfortable);
  assert(tempBand(25.9f)==TempBand::Comfortable);
  assert(tempBand(26.f)==TempBand::Warm);
  assert(tempBand(30.f)==TempBand::Hot);
  assert(tempBand(35.f)==TempBand::Extreme);
  assert(tempBand(std::numeric_limits<float>::quiet_NaN())==TempBand::Invalid);
  assert(humidityTrack(80.f,29.f)==14);
  assert(humidityTrack(80.f,23.f)==13);
  TemperatureAnnouncement a;
  assert(a.update(22.f,0)==0);
  assert(a.update(22.f,1000)==0);
  assert(a.update(22.f,2000)==3);
  assert(a.update(31.f,3000)==0);
  assert(a.update(31.f,4000)==0);
  assert(a.update(31.f,5000)==0); // cooldown
  assert(a.update(31.f,123000)==7);
  assert(a.update(31.f,124000)==0);
  return 0;
}
// g++ -std=c++17 -Wall -Wextra -pedantic CompiAudioPolicyTest.cpp -o /tmp/compi-audio-test && /tmp/compi-audio-test
