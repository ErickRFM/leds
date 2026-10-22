#pragma once
#include <stdint.h>
#include <math.h>

namespace esp32core {

struct EnvironmentalRgbInput {
  bool temperatureFresh = false;
  float temperatureC = NAN;
  uint32_t temperatureAgeMs = 0;
  bool humidityFresh = false;
  float humidityPercent = NAN;
  uint32_t humidityAgeMs = 0;
  bool lightFresh = false;
  float lightPercent = NAN;
  uint32_t lightAgeMs = 0;
};

struct EnvironmentalRgbDecision {
  bool apply = false;
  uint8_t red = 0;
  uint8_t green = 0;
  uint8_t blue = 0;
  uint8_t brightness = 210;
};

class EnvironmentalRgbPolicy {
 public:
  // Keep the color stable around the band boundaries (+/- 0.25 C).
  // The five bands are temperature-only. Humidity and LDR remain telemetry.
  EnvironmentalRgbDecision evaluate(const EnvironmentalRgbInput& input) {
    EnvironmentalRgbDecision result{};
    if (!input.temperatureFresh || !isfinite(input.temperatureC) ||
        input.temperatureAgeMs > 10000U ||
        input.temperatureC < -40.0f || input.temperatureC > 80.0f) {
      return result;  // Do not invent a color from invalid/stale DHT data.
    }

    const float t = input.temperatureC;
    uint8_t band = rawBand(t);
    if (hasBand_) {
      const float boundaries[4] = {20.0f, 26.0f, 30.0f, 35.0f};
      if (band > band_ && t < boundaries[band_] + 0.25f) band = band_;
      if (band < band_ && t >= boundaries[band - 1] - 0.25f) band = band_;
    }
    hasBand_ = true;
    band_ = band;
    result.apply = true;
    switch (band) {
      case 0: result.red = 0; result.green = 0; result.blue = 255; break;
      case 1: result.red = 0; result.green = 255; result.blue = 0; break;
      case 2: result.red = 255; result.green = 210; result.blue = 0; break;
      case 3: result.red = 255; result.green = 100; result.blue = 0; break;
      default: result.red = 255; result.green = 0; result.blue = 0; break;
    }
    return result;
  }

  void reset() { hasBand_ = false; band_ = 0; }

 private:
  static uint8_t rawBand(float t) {
    if (t < 20.0f) return 0;
    if (t < 26.0f) return 1;
    if (t < 30.0f) return 2;
    if (t < 35.0f) return 3;
    return 4;
  }
  bool hasBand_ = false;
  uint8_t band_ = 0;
};

}  // namespace esp32core
