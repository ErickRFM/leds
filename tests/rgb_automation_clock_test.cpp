#include "../firmware/esp32_rgb/core/RgbAutomationClock.h"
#include <cassert>
#include <cstdint>
using esp32core::RgbAutomationClock;
int main() {
  RgbAutomationClock s;
  using R = RgbAutomationClock::Result;
  assert(!s.configure(999, 0, true));
  assert(!s.active());
  assert(!s.configure(86400001, 0, true));
  assert(s.configure(1000, 100, true));
  assert(s.tick(1099) == R::None);
  assert(s.tick(1100) == R::TurnOn);
  assert(s.tick(2100) == R::TurnOff);
  assert(s.tick(6100) == R::TurnOff);
  assert(s.tick(6100) == R::None);
  s.stop();
  assert(s.tick(7100) == R::None);
  assert(s.configure(1000, UINT32_MAX - 400u, false));
  assert(s.tick(599u) == R::TurnOff);
  assert(s.tick(1599u) == R::TurnOn);
  assert(s.configure(2000, 0, true));
  assert(s.tick(2000) == R::TurnOn);
  assert(s.tick(3000) == R::None);
  return 0;
}
