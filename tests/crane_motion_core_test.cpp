#include <cassert>
#include <cmath>
#include "../firmware/esp32_rgb/core/CraneMotionCore.h"

using namespace esp32core::crane;

int main() {
  ServoAxis axis;
  assert(axis.snapshot().commandedDeg == 90.0f);
  assert(axis.setTarget(150.0f, 60.0f));
  axis.tick(1000);
  axis.tick(1050);
  assert(std::fabs(axis.snapshot().commandedDeg - 93.0f) < 0.001f);
  assert(axis.snapshot().moving);
  axis.tick(2050); // scheduler stall is capped at 100 ms
  assert(std::fabs(axis.snapshot().commandedDeg - 99.0f) < 0.001f);

  assert(axis.setTarget(999.0f, 30.0f)); // clamped to configured mechanical range
  assert(axis.snapshot().targetDeg == 170.0f);
  assert(!axis.setTarget(20.0f, 0.0f));
  assert(!axis.setTarget(NAN, 20.0f));
  axis.stop();
  assert(!axis.snapshot().moving);
  assert(axis.snapshot().targetDeg == axis.snapshot().commandedDeg);

  // Atomic dual-axis target: invalid speed must not move either axis.
  CraneMotionController crane;
  assert(!crane.setTargets(30.0f, 150.0f, 0.0f));
  assert(crane.base().snapshot().targetDeg == 90.0f);
  assert(crane.arm().snapshot().targetDeg == 90.0f);
  assert(crane.setTargets(30.0f, 150.0f, 45.0f));
  crane.tick(0);
  crane.tick(100);
  assert(crane.base().snapshot().commandedDeg < 90.0f);
  assert(crane.arm().snapshot().commandedDeg > 90.0f);

  // millis() wrap-around remains deterministic.
  ServoAxis wrapAxis;
  assert(wrapAxis.setTarget(100.0f, 20.0f));
  wrapAxis.tick(0xFFFFFFF0u);
  wrapAxis.tick(0x00000022u);
  assert(wrapAxis.snapshot().commandedDeg > 90.0f);

  DigitalJoystickDebouncer joystick(25);
  JoystickRaw raw{};
  raw.up = true;
  assert(joystick.sample(raw, 100) == 0);
  assert(joystick.sample(raw, 124) == 0);
  assert(joystick.sample(raw, 125) == JoystickUp);
  raw.up = false;
  assert(joystick.sample(raw, 130) == JoystickUp);
  assert(joystick.sample(raw, 155) == 0);
  raw.middle = true;
  assert(joystick.sample(raw, 200) == 0);
  assert(joystick.sample(raw, 225) == JoystickMiddle);
  return 0;
}
