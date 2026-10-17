// Host-side RGB PWM lease and reattachment contract, no ESP32 required.
#include <cassert>
#include <cstdint>
#include <iostream>
#include "stubs/Arduino.h"

static bool attached[40] = {};
static int failAttachPin = -1;
static unsigned renderCalls = 0;

inline bool ledcAttach(int pin, int frequency, int resolution) {
  assert(frequency == 5000 && resolution == 8);
  if (pin == failAttachPin) return false;
  attached[pin] = true;
  return true;
}
inline bool ledcDetach(int pin) { attached[pin] = false; return true; }
inline bool ledcWrite(int pin, int) { return attached[pin]; }

#include "../firmware/esp32_rgb/modules/LedRgbModule.h"

void render() { ++renderCalls; }
void effect(uint32_t) {}
bool effectActive() { return false; }

int main() {
  using esp32core::PinFunction;
  using esp32core::PinManager;
  PinManager manager;
  assert(manager.registerSystemPin(27, "led.rgb"));
  assert(manager.registerSystemPin(25, "led.rgb"));
  assert(manager.registerSystemPin(26, "led.rgb"));
  esp32core::LedRgbModule rgb(27, 25, 26, true, render, effect, effectActive);
  assert(rgb.begin(manager));
  assert(renderCalls == 1);
  assert(manager.leasedCount() == 3);
  assert(!rgb.configurePins(18, 19, 23));  // cannot rewire running PWM
  rgb.stop(manager);
  assert(manager.leasedCount() == 0);
  assert(!rgb.configurePins(18, 18, 23)); // duplicate signals
  assert(!rgb.configurePins(34, 19, 23)); // input-only is not PWM
  assert(!rgb.configurePins(6, 19, 23));  // flash pin is forbidden
  assert(rgb.configurePins(18, 19, 23));
  assert(rgb.redPin() == 18 && rgb.greenPin() == 19 && rgb.bluePin() == 23);
  assert(rgb.begin(manager));
  assert(manager.belongsTo(18, "led.rgb"));
  assert(manager.belongsTo(19, "led.rgb"));
  assert(manager.belongsTo(23, "led.rgb"));
  rgb.stop(manager);

  // When a final channel cannot attach, all earlier PWM claims must be
  // unwound; the transaction coordinator can then restore original GPIO.
  failAttachPin = 23;
  assert(!rgb.begin(manager));
  assert(manager.leasedCount() == 0);
  assert(!attached[18] && !attached[19] && !attached[23]);
  failAttachPin = -1;
  assert(rgb.configurePins(27, 25, 26));
  assert(rgb.begin(manager));
  assert(manager.belongsTo(27, "led.rgb"));
  assert(manager.belongsTo(25, "led.rgb"));
  assert(manager.belongsTo(26, "led.rgb"));
  rgb.stop(manager);

  // Never steal a GPIO while another component owns it.
  assert(manager.acquire(18, "sensor.dht22", PinFunction::DigitalInput));
  assert(rgb.configurePins(18, 19, 23));
  assert(!rgb.begin(manager));
  assert(manager.belongsTo(18, "sensor.dht22"));
  assert(manager.leasedCount() == 1);
  assert(manager.release(18, "sensor.dht22"));
  std::cout << "RGB runtime GPIO lease and rollback contract: PASS\n";
  return 0;
}
