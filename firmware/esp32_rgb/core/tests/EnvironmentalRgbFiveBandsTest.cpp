#include "../EnvironmentalRgbPolicy.h"
#include <cassert>
using namespace esp32core;
static EnvironmentalRgbResult reading(EnvironmentalRgbPolicy& p, float t) {
  EnvironmentalRgbInput s{};
  s.temperatureFresh = true;
  s.temperatureC = t;
  s.temperatureAgeMs = 100;
  return p.evaluate(s);
}
static void check(float temp, int r, int g, int b) {
  EnvironmentalRgbPolicy p;
  auto d = reading(p, temp);
  assert(d.apply && !d.suspended && d.red == r && d.green == g && d.blue == b);
}
int main() {
  check(19.0f, 0, 0, 255);
  check(20.0f, 0, 255, 0);
  check(25.0f, 0, 255, 0);
  check(26.0f, 255, 210, 0);
  check(28.0f, 255, 210, 0);
  check(30.0f, 255, 100, 0);
  check(34.0f, 255, 100, 0);
  check(35.0f, 255, 0, 0);
  EnvironmentalRgbPolicy p;
  auto bad = p.evaluate({});
  assert(!bad.apply && bad.suspended);
  EnvironmentalRgbInput stale{};
  stale.temperatureFresh = true;
  stale.temperatureC = 28.0f;
  stale.temperatureAgeMs = 10000;
  assert(!p.evaluate(stale).apply);
  reading(p, 35.8f);
  auto stable = reading(p, 34.9f);
  assert(stable.band == EnvironmentalRgbBand::Extreme);
  auto cooled = reading(p, 34.0f);
  assert(cooled.band == EnvironmentalRgbBand::Hot);
}
