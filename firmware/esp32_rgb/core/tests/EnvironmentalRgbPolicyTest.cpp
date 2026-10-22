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
    p.reset();
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
  p.reset();
  at(p, 35.5f);
  auto stable = at(p, 34.9f);
  assert(stable.apply && stable.red == 255 && stable.green == 0);
  auto lower = at(p, 34.7f);
  assert(lower.apply && lower.red == 255 && lower.green == 100);
  EnvironmentalRgbInput invalid{};
  assert(!p.evaluate(invalid).apply);
  invalid.temperatureFresh = true;
  invalid.temperatureC = 28.0f;
  invalid.temperatureAgeMs = 10001;
  assert(!p.evaluate(invalid).apply);
}
