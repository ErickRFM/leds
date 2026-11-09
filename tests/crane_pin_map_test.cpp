#include <cassert>
#include "firmware/esp32_rgb/core/CranePinMap.h"

int main() {
  using namespace esp32core;
  using namespace esp32core::crane;

  assert(kServoBasePin == 18);
  assert(kServoArmPin == 19);
  assert(uniqueCranePins());
  assert(pinCapabilitiesValid());

  // Regression: GPIO23 is reserved by Compi's physical report button.
  assert(contains(kReservedExistingPins,
                  sizeof(kReservedExistingPins), 23));
  assert(!contains(kJoystickPins, sizeof(kJoystickPins), 23));

  // ESP32 classic input-only joystick pins must remain input-only.
  assert(!PinManager::canUse(kJoystickUpPin, PinFunction::PwmOutput));
  assert(!PinManager::canUse(kJoystickDownPin, PinFunction::PwmOutput));
  assert(!PinManager::canUse(kJoystickLeftPin, PinFunction::PwmOutput));
  assert(!PinManager::canUse(kJoystickRightPin, PinFunction::PwmOutput));
  return 0;
}
