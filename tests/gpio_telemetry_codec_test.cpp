#include <cassert>
#include <cstring>
#include "../firmware/esp32_rgb/core/GpioTelemetryCodec.h"
using namespace esp32core;

int main() {
  PinManager pins;
  bool levels[PinManager::kPinCount] = {};
  char buffer[96] = {};
  assert(GpioTelemetryCodec::encode(pins, levels, buffer, sizeof(buffer)));
  assert(std::strstr(buffer, "19:F") != nullptr);
  assert(pins.acquire(27, "led.rgb", PinFunction::PwmOutput));
  assert(pins.acquire(19, "gpio.manual", PinFunction::DigitalOutput));
  assert(pins.acquire(18, "lab.experiment", PinFunction::DigitalOutput));
  levels[19] = true;
  assert(GpioTelemetryCodec::encode(pins, levels, buffer, sizeof(buffer)));
  assert(std::strstr(buffer, "27:R") != nullptr);
  assert(std::strstr(buffer, "19:H") != nullptr);
  assert(std::strstr(buffer, "18:E") != nullptr);
  levels[19] = false;
  assert(GpioTelemetryCodec::encode(pins, levels, buffer, sizeof(buffer)));
  assert(std::strstr(buffer, "19:L") != nullptr);
  char shortBuffer[10] = {};
  assert(!GpioTelemetryCodec::encode(pins, levels, shortBuffer, sizeof(shortBuffer)));
  return 0;
}
