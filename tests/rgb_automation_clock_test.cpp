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
  assert(s.advancedIntervals() == 4u); // delayed loop advanced 4 logical seconds
  assert(s.tick(6100) == R::None);
  assert(s.advancedIntervals() == 0u);
  s.stop();
  assert(s.tick(7100) == R::None);
  assert(s.configure(1000, UINT32_MAX - 400u, false));
  assert(s.tick(599u) == R::TurnOff);
  assert(s.tick(1599u) == R::TurnOn);
  assert(s.configure(2000, 0, true));
  assert(s.tick(2000) == R::TurnOn);
  assert(s.tick(3000) == R::None);
  // A ten-second stall cannot yield only one logical automation transition.
  assert(s.configure(1000, 0, true));
  assert(s.tick(9000) == R::TurnOn);
  assert(s.advancedIntervals() == 9u);
  assert(s.tick(10000) == R::TurnOff);
  assert(s.advancedIntervals() == 1u);
  // For even catchup, state must have the same parity as starting state.
  assert(s.configure(1000, 0, true));
  assert(s.tick(8000) == R::TurnOff);
  assert(s.advancedIntervals() == 8u);
  s.stop();
  assert(!s.active() && s.advancedIntervals() == 0u);
  assert(s.tick(20000) == R::None);
  // Confirm rollover does not violate monotonic logical interval accounting.
  assert(s.configure(1000, UINT32_MAX - 400u, false));
  assert(s.tick(1599) == R::TurnOn);
  assert(s.advancedIntervals() == 2u);
  return 0;
}
