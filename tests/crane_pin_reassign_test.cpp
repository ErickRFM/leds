// Host test: authorized runtime rewiring of the two-axis robotics crane.
// Covers configurePins validation, lease transfer, CAS predecessors and
// rollback — the module-level contract behind /v2/components/apply.
#include <cassert>
#include <cstdint>
#include <cstdio>
#include "stubs/Arduino.h"

static bool ledAttached[40] = {};
static int failAttachPin = -1;
inline bool ledcAttach(int pin, int frequency, int resolution) {
  if (frequency != 50 || resolution != 16) return false;
  if (pin == failAttachPin) return false;
  ledAttached[pin] = true;
  pinMode(pin, OUTPUT);  // A real PWM attach drives the configured pad.
  return true;
}
inline bool ledcDetach(int pin) { ledAttached[pin] = false; return true; }
inline bool ledcWrite(int pin, unsigned) { return ledAttached[pin]; }
inline int digitalRead(int pin) { return testPinValues[pin]; }
inline unsigned long testMillis = 0;
inline unsigned long millis() { return testMillis; }

#include "../firmware/esp32_rgb/modules/CraneRoboticsModule.h"

using esp32core::CraneRoboticsModule;
using esp32core::PinFunction;
using esp32core::PinManager;

int main() {
  PinManager manager;
  CraneRoboticsModule crane;

  // The audited compile-time map stays the boot default; nothing moved.
  const uint8_t defaults[7] = {18, 19, 34, 35, 36, 39, 17};
  for (size_t i = 0; i < 7; ++i) assert(crane.pin(i) == defaults[i]);

  assert(crane.begin(manager));
  assert(crane.ready());
  assert(manager.leasedCount() == 7);
  for (size_t i = 0; i < 7; ++i) {
    assert(manager.belongsTo(crane.pin(i), crane.id()));
    assert(!ledAttached[crane.pin(i)] || i < 2);  // Only servos attach PWM.
  }

  // A running axis must never jump pads: rewiring is denied while active.
  const uint8_t alternate[7] = {16, 17, 32, 33, 21, 22, 23};
  assert(!crane.configurePins(alternate));
  assert(!crane.configurePins(nullptr));
  assert(crane.pin(0) == 18 && crane.pin(6) == 17);  // Untouched.
  crane.stop(manager);
  assert(!crane.ready());
  assert(manager.leasedCount() == 0);  // Every old lease released.
  for (int pin = 0; pin < 40; ++pin) assert(!ledAttached[pin]);

  // Fails closed: duplicates, RGB-reserved pads, wrong pad direction,
  // impossible GPIO and output-only pads on inputs.
  const uint8_t duplicate[7] = {16, 16, 32, 33, 21, 22, 23};
  const uint8_t rgbStolen[7] = {25, 19, 34, 35, 36, 39, 17};
  const uint8_t inputServo[7] = {34, 19, 32, 33, 21, 22, 23};
  const uint8_t impossiblePad[7] = {16, 17, 32, 33, 21, 22, 40};
  const uint8_t badInput[7] = {16, 17, 4, 33, 21, 22, 23};
  assert(!crane.configurePins(duplicate));
  assert(!crane.configurePins(rgbStolen));
  assert(!crane.configurePins(inputServo));
  assert(!crane.configurePins(impossiblePad));
  assert(!crane.configurePins(badInput));
  assert(crane.pin(0) == 18);  // Rejected drafts never mutate state.

  // Authorized transfer: new pads acquire the lease, old pads are freed.
  assert(crane.configurePins(alternate));
  for (size_t i = 0; i < 7; ++i) assert(crane.pin(i) == alternate[i]);
  assert(crane.begin(manager));
  assert(crane.ready());
  assert(manager.leasedCount() == 7);
  assert(manager.belongsTo(16, crane.id()) && manager.belongsTo(23, crane.id()));
  assert(!manager.belongsTo(18, crane.id()));
  assert(ledAttached[16] && ledAttached[17] && !ledAttached[18]);

  // Rollback: a failed re-attach must restore the verified mapping.
  crane.stop(manager);
  assert(crane.configurePins(defaults));
  failAttachPin = 19;  // Arm servo refuses to attach.
  assert(!crane.begin(manager));
  assert(!crane.ready());
  assert(manager.leasedCount() == 0);
  failAttachPin = -1;
  assert(crane.configurePins(alternate));
  assert(crane.begin(manager));
  assert(crane.ready() && manager.belongsTo(17, crane.id()));

  crane.stop(manager);
  std::puts("Crane runtime pin reassignment: ALL OK");
  return 0;
}
