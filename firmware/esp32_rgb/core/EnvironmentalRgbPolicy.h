#pragma once
#include <cstdint>

namespace esp32core {

// Pure, bounded policy. Does NOT write GPIO or authorize a transport.
// A later orchestrator must arbitrate against manual/script/OTA modes before
// applying the returned color through the existing LedRgbModule.
struct EnvironmentalRgbConfig {
  float coldMaxC = 20.0f;
  float comfortMaxC = 26.0f;
  float warmMaxC = 30.0f;
  float hotMaxC = 35.0f;
  float hysteresisC = 0.5f;
  uint8_t minBrightness = 25;
  uint8_t maxBrightness = 180;
  uint8_t fallbackBrightness = 80;
  bool darkMeansBrighter = true;
  uint32_t maxSampleAgeMs = 6000;
};

struct EnvironmentalRgbInput {
  float temperatureC = 0.0f;
  bool temperatureFresh = false;
  uint32_t temperatureAgeMs = 0;
  float lightPercent = 0.0f;  // calibrated 0..100; never treat raw ADC as %
  bool lightFresh = false;
  uint32_t lightAgeMs = 0;
};

enum class EnvironmentalRgbBand : uint8_t {
  Cold, Comfort, Warm, Hot, Extreme
};

struct EnvironmentalRgbResult {
  bool apply = false;
  bool suspended = true;
  bool lightWasUsed = false;
  EnvironmentalRgbBand band = EnvironmentalRgbBand::Comfort;
  uint8_t red = 0, green = 0, blue = 0, brightness = 0;
};

class EnvironmentalRgbPolicy final {
 public:
  bool configure(const EnvironmentalRgbConfig& cfg) {
    if (!finite(cfg.coldMaxC) || !finite(cfg.comfortMaxC) ||
        !finite(cfg.warmMaxC) || !finite(cfg.hotMaxC) || !finite(cfg.hysteresisC) ||
        cfg.coldMaxC >= cfg.comfortMaxC ||
        cfg.comfortMaxC >= cfg.warmMaxC ||
        cfg.warmMaxC >= cfg.hotMaxC ||
        cfg.hysteresisC < 0.0f || cfg.hysteresisC > 3.0f ||
        cfg.minBrightness > cfg.maxBrightness ||
        cfg.fallbackBrightness > cfg.maxBrightness ||
        cfg.maxSampleAgeMs == 0 || cfg.maxSampleAgeMs > 60000u) {
      return false;
    }
    config_ = cfg;
    haveBand_ = false;
    return true;
  }

  EnvironmentalRgbResult evaluate(const EnvironmentalRgbInput& sample) {
    EnvironmentalRgbResult out{};
    // No fresh temperature => no autonomous changes; existing RGB retains
    // the last safe state rather than being driven by invented 0-degree values.
    if (!sample.temperatureFresh ||
        sample.temperatureAgeMs > config_.maxSampleAgeMs ||
        !finite(sample.temperatureC) ||
        sample.temperatureC < -40.0f || sample.temperatureC > 85.0f) {
      return out;
    }

    EnvironmentalRgbBand proposed = classify(sample.temperatureC);
    if (haveBand_) {
      const uint8_t current = static_cast<uint8_t>(band_);
      const uint8_t next = static_cast<uint8_t>(proposed);
      if (next > current) {
        const float boundary = boundaryAbove(current);
        if (sample.temperatureC < boundary + config_.hysteresisC) proposed = band_;
      } else if (next < current) {
        const float boundary = boundaryBelow(current);
        if (sample.temperatureC >= boundary - config_.hysteresisC) proposed = band_;
      }
    }
    band_ = proposed;
    haveBand_ = true;

    out.band = band_;
    out.suspended = false;
    out.apply = true;
    switch (band_) {
      case EnvironmentalRgbBand::Cold: out.red=0; out.green=0; out.blue=255; break;
      case EnvironmentalRgbBand::Comfort: out.red=0; out.green=255; out.blue=0; break;
      case EnvironmentalRgbBand::Warm: out.red=255; out.green=210; out.blue=0; break;
      case EnvironmentalRgbBand::Hot: out.red=255; out.green=100; out.blue=0; break;
      case EnvironmentalRgbBand::Extreme: out.red=255; out.green=0; out.blue=0; break;
    }
    out.brightness = config_.fallbackBrightness;
    if (sample.lightFresh && sample.lightAgeMs <= config_.maxSampleAgeMs &&
        finite(sample.lightPercent) &&
        sample.lightPercent >= 0.0f && sample.lightPercent <= 100.0f) {
      const float normalized = (config_.darkMeansBrighter
          ? 100.0f - sample.lightPercent : sample.lightPercent) / 100.0f;
      const float scaled = static_cast<float>(config_.minBrightness) +
          normalized * static_cast<float>(
              config_.maxBrightness - config_.minBrightness);
      out.brightness = static_cast<uint8_t>(scaled + 0.5f);
      out.lightWasUsed = true;
    }
    return out;
  }

  void reset() { haveBand_ = false; }
  bool initialized() const { return haveBand_; }

 private:
  static bool finite(float x) { return x == x && x >= -1.0e30f && x <= 1.0e30f; }
  EnvironmentalRgbBand classify(float c) const {
    if (c < config_.coldMaxC) return EnvironmentalRgbBand::Cold;
    if (c < config_.comfortMaxC) return EnvironmentalRgbBand::Comfort;
    if (c < config_.warmMaxC) return EnvironmentalRgbBand::Warm;
    if (c < config_.hotMaxC) return EnvironmentalRgbBand::Hot;
    return EnvironmentalRgbBand::Extreme;
  }
  float boundaryAbove(uint8_t band) const {
    if (band == 0) return config_.coldMaxC;
    if (band == 1) return config_.comfortMaxC;
    if (band == 2) return config_.warmMaxC;
    return config_.hotMaxC;
  }
  float boundaryBelow(uint8_t band) const {
    if (band == 4) return config_.hotMaxC;
    if (band == 3) return config_.warmMaxC;
    if (band == 2) return config_.comfortMaxC;
    return config_.coldMaxC;
  }
  EnvironmentalRgbConfig config_{};
  EnvironmentalRgbBand band_ = EnvironmentalRgbBand::Comfort;
  bool haveBand_ = false;
};

}  // namespace esp32core
