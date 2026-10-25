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
  float humidityPercent = 0.0f;
  bool humidityFresh = false;
  uint32_t humidityAgeMs = 0;
  float lightPercent = 0.0f;  // calibrated 0..100; never treat raw ADC as %
  bool lightFresh = false;
  uint32_t lightAgeMs = 0;
};

enum class EnvironmentalRgbBand : uint8_t {
  Cold, Comfort, Warm, Hot, Extreme
};

enum class EnvironmentalRgbState : uint8_t {
  Disabled,
  Running,
  Suspended,
  Conflict,
  Error
};

inline const char* environmentalRgbStateName(EnvironmentalRgbState state) {
  switch (state) {
    case EnvironmentalRgbState::Disabled: return "DISABLED";
    case EnvironmentalRgbState::Running: return "RUNNING";
    case EnvironmentalRgbState::Suspended: return "SUSPENDED";
    case EnvironmentalRgbState::Conflict: return "CONFLICT";
    case EnvironmentalRgbState::Error: return "ERROR";
  }
  return "DISABLED";
}

inline const char* environmentalRgbBandName(EnvironmentalRgbBand band) {
  switch (band) {
    case EnvironmentalRgbBand::Cold: return "Cold";
    case EnvironmentalRgbBand::Comfort: return "Comfort";
    case EnvironmentalRgbBand::Warm: return "Warm";
    case EnvironmentalRgbBand::Hot: return "Hot";
    case EnvironmentalRgbBand::Extreme: return "Extreme";
  }
  return "Comfort";
}

struct EnvironmentalRgbSnapshot {
  bool enabled = false;
  EnvironmentalRgbState state = EnvironmentalRgbState::Disabled;
  const char* reason = "DISABLED";
  EnvironmentalRgbBand band = EnvironmentalRgbBand::Comfort;
  uint8_t red = 0;
  uint8_t green = 0;
  uint8_t blue = 0;
  uint8_t brightness = 0;
  bool humidityUsed = false;
  bool lightUsed = false;
  uint32_t lastAppliedMs = 0;
};

struct EnvironmentalRgbResult {
  bool apply = false;
  bool suspended = true;
  bool lightWasUsed = false;
  bool humidityWasUsed = false;
  EnvironmentalRgbBand band = EnvironmentalRgbBand::Comfort;
  uint8_t red = 0, green = 0, blue = 0, brightness = 0;
};

// Shared confirmation contract, exercised by the host-side C++ regression test.
// A software acceptance is NOT independent electrical readback.
inline void confirmEnvironmentalRgbApplication(EnvironmentalRgbSnapshot& state,
    const EnvironmentalRgbResult& output, bool needsApply,
    bool accepted, uint32_t nowMs) {
  if (!accepted) {
    state.state = EnvironmentalRgbState::Error;
    state.reason = "COMMAND_REJECTED";
    return;
  }
  state.state = EnvironmentalRgbState::Running;
  state.reason = "OK";
  state.band = output.band;
  state.red = output.red;
  state.green = output.green;
  state.blue = output.blue;
  state.brightness = output.brightness;
  state.humidityUsed = output.humidityWasUsed;
  state.lightUsed = output.lightWasUsed;
  if (needsApply) state.lastAppliedMs = nowMs;
}

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
      case EnvironmentalRgbBand::Cold: out.red=52; out.green=120; out.blue=237; break;
      case EnvironmentalRgbBand::Comfort: out.red=54; out.green=186; out.blue=116; break;
      case EnvironmentalRgbBand::Warm: out.red=246; out.green=184; out.blue=63; break;
      case EnvironmentalRgbBand::Hot: out.red=255; out.green=125; out.blue=20; break;
      case EnvironmentalRgbBand::Extreme: out.red=230; out.green=35; out.blue=35; break;
    }
    // Temperature owns the five base bands. Valid humidity adds a restrained
    // tint without changing band thresholds or LDR-driven brightness.
    if (sample.humidityFresh && sample.humidityAgeMs <= config_.maxSampleAgeMs &&
        finite(sample.humidityPercent) && sample.humidityPercent >= 0.0f &&
        sample.humidityPercent <= 100.0f) {
      out.humidityWasUsed = true;
      if (sample.humidityPercent >= 70.0f) {
        // Humid air: gently shift toward blue, independent of temperature.
        // A humidity accent must never turn the comfortable GREEN band cyan.
        // Limit added blue to a small accent and preserve the dominant channel.
        const unsigned suggestedBlue =
            (static_cast<unsigned>(out.blue) * 7u + 255u) / 8u;
        const unsigned greenSafeBlue = (band_ == EnvironmentalRgbBand::Comfort)
            ? static_cast<unsigned>(out.green) / 2u : 255u;
        out.blue = static_cast<uint8_t>(
            suggestedBlue < greenSafeBlue ? suggestedBlue : greenSafeBlue);
      } else if (sample.humidityPercent <= 30.0f) {
        // Dry air: warm the same base band with a subtle red accent.
        out.red = static_cast<uint8_t>(
            (static_cast<unsigned>(out.red) * 3u + 255u) / 4u);
      }
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
