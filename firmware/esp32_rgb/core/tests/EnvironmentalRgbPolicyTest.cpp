#include "../EnvironmentalRgbPolicy.h"
#include <cassert>
using namespace esp32core;

static EnvironmentalRgbDecision at(EnvironmentalRgbPolicy& p, float t) {
  EnvironmentalRgbInput in{};
  in.temperatureFresh = true;
  in.temperatureC = t;
  in.temperatureAgeMs = 500;
  return p.evaluate(in);
}
int main() {
  EnvironmentalRgbPolicy p;
  auto check = [&](float t, int r, int g, int b) {
    auto d = at(p, t);
    assert(d.apply && d.red == r && d.green == g && d.blue == b);
  };
  check(19.0f, 0, 0, 255);
  check(20.0f, 0, 255, 0);
  check(25.0f, 0, 255, 0);
  check(26.0f, 255, 210, 0);
  check(28.0f, 255, 210, 0);
  check(30.0f, 255, 100, 0);
  check(35.0f, 255, 0, 0);
  check(34.9f, 255, 0, 0); // no flicker at 35 C
  check(34.7f, 255, 100, 0);
  EnvironmentalRgbInput invalid{};
  assert(!p.evaluate(invalid).apply);
  invalid.temperatureFresh = true;
  invalid.temperatureC = 28.0f;
  invalid.temperatureAgeMs = 10001;
  assert(!p.evaluate(invalid).apply);
}
