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
// Calibration currently requires darkRaw < brightRaw (min span 32).
//
// Reports the raw sample as AdcRaw, and relative ambient_light percentage (0-100%)
// when calibrated with darkRaw and brightRaw reference points.
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
    haveSample_ = false;
    rawReading_ = {MeasurementType::AmbientLightRaw, MeasurementStatus::Absent, false, 0.0f, "raw", 0};
    lightReading_ = {MeasurementType::AmbientLight, MeasurementStatus::NotCalibrated, false, 0.0f, "%", 0};
    return true;
  }

  // Calibration: darkRaw (lower ADC value) and brightRaw (higher ADC value)
  bool setCalibration(uint16_t darkRaw, uint16_t brightRaw) {
    if (darkRaw > kAdcMax || brightRaw > kAdcMax ||
        darkRaw >= brightRaw || brightRaw - darkRaw < kMinCalibrationSpan) return false;
    darkRaw_ = darkRaw;
    brightRaw_ = brightRaw;
    calibrated_ = true;
    return true;
  }

  void clearCalibration() { calibrated_ = false; }

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
    } else {
      filtered_ += (static_cast<float>(raw) - filtered_) / 4.0f;
    }
    rawReading_ = {MeasurementType::AmbientLightRaw, MeasurementStatus::Fresh, true,
                   static_cast<float>(raw), "raw", nowMs};
    if (!calibrated_) {
      lightReading_ = {MeasurementType::AmbientLight,
                       MeasurementStatus::NotCalibrated, false, 0.0f, "%", nowMs};
      return true;
    }
    lightReading_ = {MeasurementType::AmbientLight, MeasurementStatus::Fresh,
                     true, percentFromFiltered(), "%", nowMs};
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
  Measurement rawReading_{};
  Measurement lightReading_{};
};

}  // namespace esp32core
