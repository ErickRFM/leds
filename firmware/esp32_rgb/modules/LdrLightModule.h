#pragma once

#include <cstdint>
#include "../core/ModuleRegistry.h"
#include "../core/SensorTelemetry.h"

namespace esp32core {

// LDR sensor on an ADC1 pin (Compi lab default GPIO32).
// Four-pin comparator modules must connect AO (analog output) to GPIO32,
// VCC to 3V3, GND to GND; leave DO unconnected.
// A discrete two-terminal photoresistor also needs a voltage divider.
// For a divider 3V3 -> LDR -> ADC node -> 10k -> GND, greater light
// normally raises the ADC value. Other module topologies can invert it.
// Calibration supports either ADC polarity (minimum separation 32).
//
// Reports a diagnostic raw ADC sample and a relative light percentage (0-100%).
// Until explicit dark/bright calibration exists, a provisional estimate uses
// 0 as dark and a learned stable bright reference (starting at 1024 ADC).
// That estimate is NOT lux or an absolute lighting measurement: physically
// captured calibration always takes priority and supports inverted modules.
class LdrLightModule final : public IModule {
 public:
  using AdcReadFn = int (*)(uint8_t pin, void* context);

  LdrLightModule(uint8_t adcPin, AdcReadFn read, void* context,
                 uint32_t sampleIntervalMs = 500)
      : pin_(adcPin), read_(read), context_(context),
        sampleIntervalMs_(sampleIntervalMs) {}

  const char* id() const override { return "sensor.ldr_light"; }
  uint16_t version() const override { return 1; }

  bool begin(PinManager& manager) override {
    if (ready_) return false;
    if (read_ == nullptr) return false;
    // ADC1 only: ADC2 is unusable while Wi-Fi is active.
    if (!PinManager::analogInputCapable(pin_)) return false;
    if (!manager.acquire(pin_, id(), PinFunction::AnalogInput)) return false;
    ready_ = true;
    lastSampleMs_ = 0;
    raw_ = 0;
    filtered_ = 0.0f;
    estimatedBrightRaw_ = kInitialBrightReference;
    brightCandidateSamples_ = 0;
    haveSample_ = false;
    rawReading_ = {MeasurementType::AmbientLightRaw, MeasurementStatus::Absent, false, 0.0f, "raw", 0};
    lightReading_ = {MeasurementType::AmbientLight, MeasurementStatus::NotCalibrated, false, 0.0f, "%", 0};
    return true;
  }

  // Calibration is defined by physical conditions, not ADC ordering: inverted modules supported.
  bool setCalibration(uint16_t darkRaw, uint16_t brightRaw) {
    if (darkRaw > kAdcMax || brightRaw > kAdcMax ||
        darkRaw == brightRaw || (darkRaw > brightRaw ? darkRaw - brightRaw : brightRaw - darkRaw) < kMinCalibrationSpan) return false;
    darkRaw_ = darkRaw;
    brightRaw_ = brightRaw;
    calibrated_ = true;
    brightCandidateSamples_ = 0;
    return true;
  }

  void clearCalibration() {
    calibrated_ = false;
    estimatedBrightRaw_ = kInitialBrightReference;
    brightCandidateSamples_ = 0;
  }

  void tick(uint32_t nowMs) override {
    if (!ready_ || read_ == nullptr) return;
    if (haveSample_ &&
        static_cast<uint32_t>(nowMs - lastSampleMs_) < sampleIntervalMs_) {
      return;
    }
    lastSampleMs_ = nowMs;
    const int sample = read_(pin_, context_);
    if (sample < 0 || sample > kAdcMax) {
      rawReading_ = {MeasurementType::AmbientLightRaw, MeasurementStatus::SensorError,
                     false, 0.0f, "raw", nowMs};
      lightReading_ = {MeasurementType::AmbientLight, MeasurementStatus::SensorError,
                       false, 0.0f, "%", nowMs};
      return;
    }
    applySample(static_cast<uint16_t>(sample), nowMs);
  }

  bool applySample(uint16_t raw, uint32_t nowMs) {
    if (!ready_ || raw > kAdcMax) return false;
    raw_ = raw;
    if (!haveSample_) {
      filtered_ = static_cast<float>(raw);
      haveSample_ = true;
    } else if (!calibrated_ && raw <= kNearDarkRaw) {
      // A covered sensor can legitimately read zero. Announce darkness promptly
      // rather than retaining several seconds of the previous bright average.
      filtered_ = 0.0f;
    } else {
      filtered_ += (static_cast<float>(raw) - filtered_) / 4.0f;
    }
    rawReading_ = {MeasurementType::AmbientLightRaw, MeasurementStatus::Fresh, true,
                   static_cast<float>(raw), "raw", nowMs};
    // No explicit calibration yet? Provide a visibly useful RELATIVE estimate
    // for the display and spoken report. No raw ADC is passed as a percent.
    const float relativePercent = calibrated_ ? percentFromFiltered()
                                               : estimatePercent(raw);
    lightReading_ = {MeasurementType::AmbientLight, MeasurementStatus::Fresh,
                     true, relativePercent, "%", nowMs};
    return true;
  }

  void stop(PinManager& manager) override {
    if (!ready_) return;
    manager.release(pin_, id());
    ready_ = false;
    haveSample_ = false;
    rawReading_.status = MeasurementStatus::Absent;
    rawReading_.valid = false;
    lightReading_.status = MeasurementStatus::Absent;
    lightReading_.valid = false;
  }

  static constexpr uint16_t kAdcMax = 4095;
  static constexpr uint16_t kMinCalibrationSpan = 32;
  static constexpr uint16_t kInitialBrightReference = 1024;
  static constexpr uint16_t kNearDarkRaw = 16;

  bool configurePin(uint8_t next) {
    if (ready_ || !PinManager::analogInputCapable(next) ||
        PinManager::reservedForRgb(next)) return false;
    if (pin_ != next) {
      pin_ = next;
      clearCalibration();
      darkRaw_ = 0;
      brightRaw_ = 0;
      lightReading_ = {MeasurementType::AmbientLight,
                       MeasurementStatus::NotCalibrated, false, 0.0f, "%", 0};
    }
    return true;
  }

  bool ready() const { return ready_; }
  bool calibrated() const { return calibrated_; }
  uint8_t pin() const { return pin_; }
  uint16_t raw() const { return raw_; }
  uint16_t darkRaw() const { return darkRaw_; }
  uint16_t brightRaw() const { return brightRaw_; }
  const Measurement& rawReading() const { return rawReading_; }
  const Measurement& lightReading() const { return lightReading_; }

 private:
  float estimatePercent(uint16_t raw) {
    // The user's LDR wiring rises with light and approaches zero in darkness.
    // Learn higher bright references only after 3 consecutive raw samples;
    // a single ADC spike must not permanently ruin the visible percentage.
    if (raw >= estimatedBrightRaw_ + 100.0f) {
      if (++brightCandidateSamples_ >= 3) {
        if (filtered_ > estimatedBrightRaw_) estimatedBrightRaw_ = filtered_;
        brightCandidateSamples_ = 0;
      }
    } else {
      brightCandidateSamples_ = 0;
    }
    const float percent = filtered_ * 100.0f / estimatedBrightRaw_;
    if (percent < 0.0f) return 0.0f;
    if (percent > 100.0f) return 100.0f;
    return percent;
  }
  float percentFromFiltered() const {
    const float span = static_cast<float>(brightRaw_) - static_cast<float>(darkRaw_);
    if (span == 0.0f) return 0.0f;
    float percent = (filtered_ - static_cast<float>(darkRaw_)) * 100.0f / span;
    if (percent < 0.0f) percent = 0.0f;
    if (percent > 100.0f) percent = 100.0f;
    return percent;
  }

  uint8_t pin_;
  const AdcReadFn read_;
  void* const context_;
  const uint32_t sampleIntervalMs_;
  bool ready_ = false;
  bool calibrated_ = false;
  bool haveSample_ = false;
  uint32_t lastSampleMs_ = 0;
  uint16_t raw_ = 0;
  uint16_t darkRaw_ = 0;
  uint16_t brightRaw_ = 0;
  float filtered_ = 0.0f;
  float estimatedBrightRaw_ = kInitialBrightReference;
  uint8_t brightCandidateSamples_ = 0;
  Measurement rawReading_{};
  Measurement lightReading_{};
};

}  // namespace esp32core
