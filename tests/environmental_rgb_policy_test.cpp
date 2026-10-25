#include <cassert>
#include <cstring>
#include <cmath>
#include <cstdint>
#include <iostream>
#include "../firmware/esp32_rgb/core/EnvironmentalRgbPolicy.h"
#include "../firmware/esp32_rgb/core/BleEnvironmentalCommand.h"

using namespace esp32core;

int main() {
  EnvironmentalRgbPolicy policy;
  EnvironmentalRgbInput reading{};
  // A missing DHT11 never generates a fake color or changes PWM.
  assert(!policy.evaluate(reading).apply);
  assert(!policy.initialized());

  // 22.7 °C: comfort must be visibly GREEN, with minimal blue/red
  reading.temperatureFresh = true;
  reading.temperatureAgeMs = 100;
  reading.temperatureC = 22.7f;
  reading.humidityFresh = true;
  reading.humidityAgeMs = 100;
  reading.humidityPercent = 50.0f; // Normal humidity (30-70%) must NOT alter green
  auto result = policy.evaluate(reading);
  assert(result.apply && !result.suspended);
  assert(result.band == EnvironmentalRgbBand::Comfort);
  assert(result.red == 0 && result.green == 255 && result.blue == 0);

  reading.temperatureC = 18.0f;
  result = policy.evaluate(reading);
  assert(result.apply && !result.suspended);
  assert(result.band == EnvironmentalRgbBand::Cold);
  assert(result.blue == 255 && result.red == 0 && result.green == 0 && result.brightness == 80);
  assert(!result.lightWasUsed);

  // Hysteresis: jitter around 20°C does not flash between blue/green.
  reading.temperatureC = 20.1f;
  result = policy.evaluate(reading);
  assert(result.band == EnvironmentalRgbBand::Cold);
  reading.temperatureC = 20.6f;
  result = policy.evaluate(reading);
  assert(result.band == EnvironmentalRgbBand::Comfort);
  reading.temperatureC = 19.9f;
  result = policy.evaluate(reading);
  assert(result.band == EnvironmentalRgbBand::Comfort);
  reading.temperatureC = 19.4f;
  assert(policy.evaluate(reading).band == EnvironmentalRgbBand::Cold);

  reading.temperatureC = 26.0f;
  assert(policy.evaluate(reading).band == EnvironmentalRgbBand::Warm);
  reading.temperatureC = 30.0f;
  assert(policy.evaluate(reading).band == EnvironmentalRgbBand::Warm);
  reading.temperatureC = 30.6f;
  assert(policy.evaluate(reading).band == EnvironmentalRgbBand::Hot);
  reading.temperatureC = 35.0f;
  assert(policy.evaluate(reading).band == EnvironmentalRgbBand::Hot);
  reading.temperatureC = 35.6f;
  assert(policy.evaluate(reading).band == EnvironmentalRgbBand::Extreme);
  reading.temperatureC = 34.8f;
  assert(policy.evaluate(reading).band == EnvironmentalRgbBand::Extreme);
  reading.temperatureC = 35.6f;

  // Humidity adjusts color only when the same DHT reports usable humidity.
  reading.humidityFresh = true;
  reading.humidityAgeMs = 100;
  reading.humidityPercent = 75.0f;
  result = policy.evaluate(reading);
  assert(result.humidityWasUsed);
  assert(result.band == EnvironmentalRgbBand::Extreme);
  assert(result.red == 255 && result.green == 0 && result.blue == 0);
  // Regression: 23 C at 75% humidity must stay green, not cyan.
  reading.temperatureC = 23.0f;
  result = policy.evaluate(reading);
  assert(result.band == EnvironmentalRgbBand::Comfort);
  assert(result.green == 255 && result.blue == 0);
  assert(result.green > result.red);
  reading.temperatureC = 35.6f;
  reading.humidityPercent = 25.0f;
  result = policy.evaluate(reading);
  assert(result.humidityWasUsed && result.red == 255 && result.green == 0 && result.blue == 0);
  reading.humidityAgeMs = 7000;
  result = policy.evaluate(reading);
  assert(!result.humidityWasUsed && result.red == 255);
  reading.humidityAgeMs = 0;
  reading.humidityPercent = std::nanf("");
  assert(!policy.evaluate(reading).humidityWasUsed);
  reading.humidityPercent = 50.0f;
  reading.humidityFresh = false;
  assert(!policy.evaluate(reading).humidityWasUsed);

  // Calibrated darkness raises brightness; no lux or raw ADC assumptions.
  reading.lightFresh = true;
  reading.lightAgeMs = 200;
  reading.lightPercent = 0.0f;
  result = policy.evaluate(reading);
  assert(result.lightWasUsed && result.brightness == 180);
  reading.lightPercent = 100.0f;
  assert(policy.evaluate(reading).brightness == 25);
  reading.lightPercent = 50.0f;
  assert(policy.evaluate(reading).brightness > 25);
  assert(policy.evaluate(reading).brightness < 180);

  // Invalid light retains bounded fallback, never converts raw ADC to percent.
  reading.lightPercent = 500.0f;
  result = policy.evaluate(reading);
  assert(!result.lightWasUsed && result.brightness == 80);
  reading.lightPercent = 20.0f;
  reading.lightAgeMs = 7000;
  assert(!policy.evaluate(reading).lightWasUsed);
  reading.lightFresh = false;
  assert(policy.evaluate(reading).brightness == 80);

  // Stale, implausible and NaN DHT readings suspend automatic control.
  reading.temperatureAgeMs = 7000;
  assert(!policy.evaluate(reading).apply);
  reading.temperatureAgeMs = 0;
  reading.temperatureC = std::nanf("");
  assert(!policy.evaluate(reading).apply);
  reading.temperatureC = 200.0f;
  assert(!policy.evaluate(reading).apply);

  EnvironmentalRgbConfig invalid{};
  invalid.coldMaxC = 27.0f;
  invalid.comfortMaxC = 24.0f;
  assert(!policy.configure(invalid));
  invalid = EnvironmentalRgbConfig{};
  invalid.maxBrightness = 10;
  invalid.minBrightness = 20;
  assert(!policy.configure(invalid));

  EnvironmentalRgbConfig flipped{};
  flipped.darkMeansBrighter = false;
  assert(policy.configure(flipped));
  reading.temperatureC = 22.0f;
  reading.lightFresh = true;
  reading.lightAgeMs = 0;
  reading.lightPercent = 100.0f;
  result = policy.evaluate(reading);
  assert(result.lightWasUsed && result.brightness == 180);
  policy.reset();
  assert(!policy.initialized());

  // RUNNING is never reported before successful software application.
  EnvironmentalRgbSnapshot live{};
  live.lastAppliedMs = 42;
  reading = EnvironmentalRgbInput{};
  reading.temperatureFresh = true;
  reading.temperatureC = 23.0f;
  reading.humidityFresh = true;
  reading.humidityPercent = 50.0f;
  const auto desired = policy.evaluate(reading);
  assert(desired.apply && desired.band == EnvironmentalRgbBand::Comfort);
  confirmEnvironmentalRgbApplication(live, desired, true, false, 100);
  assert(live.state == EnvironmentalRgbState::Error);
  assert(std::strcmp(live.reason,"COMMAND_REJECTED")==0);
  assert(live.lastAppliedMs == 42);
  confirmEnvironmentalRgbApplication(live, desired, true, true, 200);
  assert(live.state == EnvironmentalRgbState::Running);
  assert(live.green == 255 && live.red == 0 && live.blue == 0);
  assert(live.lastAppliedMs == 200);
  // Identical channels: no PWM write, and no invented application timestamp.
  confirmEnvironmentalRgbApplication(live, desired, false, true, 300);
  assert(live.lastAppliedMs == 200);

  std::cout << "environmental RGB policy: ALL OK\n";
  return 0;
}
