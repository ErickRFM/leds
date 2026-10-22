#pragma once

#include <cstdint>
#include "../core/ModuleRegistry.h"
#include "../core/SensorTelemetry.h"

namespace esp32core {

// Analog capacitive soil moisture sensor on an ADC1 pin.
//
// The raw ADC value is meaningless as a percentage until the user calibrates
// the dry and wet reference points. This module therefore reports the raw
// sample as AdcRaw and only publishes a soil_moisture percentage when a valid
// calibration exists; otherwise the measurement status is NotCalibrated.
//
// A small exponential moving average tames ADC noise without hiding a real,
// sustained change. Read is injected for host testability.
class SoilMoistureModule final : public IModule {
 public:
  using AdcReadFn = int (*)(uint8_t pin, void* context);

  SoilMoistureModule(uint8_t adcPin, AdcReadFn read, void* context,
                     uint32_t sampleIntervalMs = 1000)
      : pin_(adcPin), read_(read), context_(context),
        sampleIntervalMs_(sampleIntervalMs) {}

  const char* id() const override { return "sensor.soil_moisture"; }
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
    filtered_ = 0;
    haveSample_ = false;
    rawReading_ = {MeasurementType::AdcRaw, MeasurementStatus::Absent, false, 0.0f, "raw", 0};
    moistureReading_ = {MeasurementType::SoilMoisture, MeasurementStatus::NotCalibrated, false, 0.0f, "%", 0};
    return true;
  }

  // Both reference points must be distinct valid 12-bit ADC readings.
  bool setCalibration(uint16_t dryRaw, uint16_t wetRaw) {
    if (dryRaw > kAdcMax || wetRaw > kAdcMax || dryRaw == wetRaw) return false;
    dryRaw_ = dryRaw;
    wetRaw_ = wetRaw;
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
    if (!applySample(clampRaw(read_(pin_, context_)), nowMs)) return;
  }

  // Callable directly (host tests / synchronous probe) guaranteeing the same
  // validation and filtering path as the periodic tick.
  bool applySample(uint16_t raw, uint32_t nowMs) {
    if (!ready_) return false;
    raw_ = raw;
    if (!haveSample_) {
      filtered_ = static_cast<float>(raw);
      haveSample_ = true;
    } else {
      filtered_ += (static_cast<float>(raw) - filtered_) / 4.0f;
    }
    rawReading_ = {MeasurementType::AdcRaw, MeasurementStatus::Fresh, true,
                   static_cast<float>(raw), "raw", nowMs};
    if (!calibrated_) {
      moistureReading_ = {MeasurementType::SoilMoisture,
                          MeasurementStatus::NotCalibrated, false, 0.0f, "%", nowMs};
      return true;
    }
    moistureReading_ = {MeasurementType::SoilMoisture, MeasurementStatus::Fresh,
                        true, percentFromFiltered(), "%", nowMs};
    return true;
  }

  void stop(PinManager& manager) override {
    if (!ready_) return;
    manager.release(pin_, id());
    ready_ = false;
    haveSample_ = false;
  }

  static constexpr uint16_t kAdcMax = 4095;

  bool configurePin(uint8_t next) {
    if (ready_ || !PinManager::analogInputCapable(next) ||
        PinManager::reservedForRgb(next)) return false;
    if (pin_ != next) {
      pin_ = next;
      // A new ADC1 source is not calibrated with the previous sensor's dry
      // and wet reference points. Even a failed apply/rollback must never
      // advertise old percentages against a potentially different probe.
      clearCalibration();
      dryRaw_ = 0;
      wetRaw_ = 0;
      moistureReading_ = {MeasurementType::SoilMoisture,
                          MeasurementStatus::NotCalibrated, false, 0.0f, "%", 0};
    }
    return true;
  }
  bool ready() const { return ready_; }
  bool calibrated() const { return calibrated_; }
  uint8_t pin() const { return pin_; }
  uint16_t raw() const { return raw_; }
  uint16_t dryRaw() const { return dryRaw_; }
  uint16_t wetRaw() const { return wetRaw_; }
  const Measurement& rawReading() const { return rawReading_; }
  const Measurement& moistureReading() const { return moistureReading_; }

  enum class Band : uint8_t { Dry, Normal, Wet };
  // Thresholds are relative to the calibrated span; without calibration the
  // band is always Normal because no physical meaning can be asserted.
  Band band() const {
    if (!calibrated_ || !moistureReading_.valid) return Band::Normal;
    if (moistureReading_.value < 30.0f) return Band::Dry;
    if (moistureReading_.value > 70.0f) return Band::Wet;
    return Band::Normal;
  }

  static uint16_t clampRaw(int value) {
    if (value < 0) return 0;
    if (value > kAdcMax) return kAdcMax;
    return static_cast<uint16_t>(value);
  }

 private:
  float percentFromFiltered() const {
    const float span = static_cast<float>(dryRaw_) - static_cast<float>(wetRaw_);
    if (span == 0.0f) return 0.0f;
    float percent = (static_cast<float>(dryRaw_) - filtered_) * 100.0f / span;
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
  uint16_t dryRaw_ = 0;
  uint16_t wetRaw_ = 0;
  float filtered_ = 0.0f;
  Measurement rawReading_{};
  Measurement moistureReading_{};
};
}  // namespace esp32core
