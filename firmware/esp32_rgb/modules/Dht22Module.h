#pragma once

#include <cstdint>
#include "../core/ModuleRegistry.h"
#include "../core/SensorTelemetry.h"

namespace esp32core {

// DHT22 (AM2302) temperature + humidity module.
//
// The module owns timing, validation, retry/backoff, freshness and pin
// lifecycle. The physical read is injected through a function pointer so the
// exact same logic is exercised by host tests with a deterministic sensor
// double and by the ESP32 build with the real bit-banged driver.
//
// It never fabricates a reading: when the sensor is absent or failing it emits
// Absent/Stale/SensorError and marks the sample invalid.
class Dht22Module final : public IModule {
 public:
  struct Reading {
    bool ok;
    float humidityPercent;
    float temperatureC;
  };
  using ReadFn = Reading (*)(void* context);

  Dht22Module(uint8_t dataPin, ReadFn read, void* context,
              uint32_t minIntervalMs = 2000)
      : pin_(dataPin), read_(read), context_(context),
        minIntervalMs_(minIntervalMs) {}

  const char* id() const override { return "sensor.dht22"; }
  uint16_t version() const override { return 1; }

  bool begin(PinManager& manager) override {
    if (ready_) return false;
    if (read_ == nullptr || manager.isSystemReserved(pin_) ||
        PinManager::reservedForRgb(pin_)) {
      return false;
    }
    // DHT needs a bidirectional pin: it drives the line low to start a read.
    if (!PinManager::outputCapable(pin_)) return false;
    if (!manager.acquire(pin_, id(), PinFunction::DigitalInput)) return false;
    ready_ = true;
    lastAttemptMs_ = 0;
    consecutiveFailures_ = 0;
    humidity_ = {MeasurementType::Humidity, MeasurementStatus::Absent, false, 0.0f, "%", 0};
    temperature_ = {MeasurementType::Temperature, MeasurementStatus::Absent, false, 0.0f, "C", 0};
    return true;
  }

  void tick(uint32_t nowMs) override {
    if (!ready_ || read_ == nullptr) return;
    // Guard against wraparound using unsigned arithmetic.
    if (lastAttemptMs_ != 0 &&
        static_cast<uint32_t>(nowMs - lastAttemptMs_) < minIntervalMs_) {
      return;
    }
    lastAttemptMs_ = nowMs;
    const Reading sample = read_(context_);
    if (sample.ok && sample.humidityPercent >= 0.0f &&
        sample.humidityPercent <= 100.0f &&
        sample.temperatureC >= -40.0f && sample.temperatureC <= 85.0f) {
      consecutiveFailures_ = 0;
      humidity_ = {MeasurementType::Humidity, MeasurementStatus::Fresh, true,
                   sample.humidityPercent, "%", nowMs};
      temperature_ = {MeasurementType::Temperature, MeasurementStatus::Fresh, true,
                      sample.temperatureC, "C", nowMs};
    } else {
      if (consecutiveFailures_ < 65535) ++consecutiveFailures_;
      const MeasurementStatus status = consecutiveFailures_ >= kMaxFailures
          ? MeasurementStatus::SensorError : MeasurementStatus::Stale;
      humidity_.status = status;
      humidity_.valid = false;
      temperature_.status = status;
      temperature_.valid = false;
    }
  }

  void stop(PinManager& manager) override {
    if (!ready_) return;
    manager.release(pin_, id());
    ready_ = false;
    consecutiveFailures_ = 0;
    lastAttemptMs_ = 0;
    humidity_ = {MeasurementType::Humidity, MeasurementStatus::Absent, false, 0.0f, "%", 0};
    temperature_ = {MeasurementType::Temperature, MeasurementStatus::Absent, false, 0.0f, "C", 0};
  }

  bool ready() const { return ready_; }
  uint8_t pin() const { return pin_; }
  // Only while stopped. The transaction manager owns stop/restart/rollback.
  bool setPin(uint8_t next) {
    if (ready_ || !PinManager::outputCapable(next) ||
        PinManager::reservedForRgb(next)) return false;
    pin_ = next;
    return true;
  }
  uint16_t consecutiveFailures() const { return consecutiveFailures_; }
  MeasurementStatus status() const { return humidity_.status; }
  const Measurement& humidityReading() const { return humidity_; }
  const Measurement& temperatureReading() const { return temperature_; }
  float humidity() const { return humidity_.value; }
  float temperature() const { return temperature_.value; }

  static constexpr uint16_t kMaxFailures = 3;
  static constexpr float kMinHumidity = 0.0f;
  static constexpr float kMaxHumidity = 100.0f;
  static constexpr float kMinTemperature = -40.0f;
  static constexpr float kMaxTemperature = 85.0f;

 private:
  uint8_t pin_;
  const ReadFn read_;
  void* const context_;
  const uint32_t minIntervalMs_;
  bool ready_ = false;
  uint32_t lastAttemptMs_ = 0;
  uint16_t consecutiveFailures_ = 0;
  Measurement humidity_{};
  Measurement temperature_{};
};
}  // namespace esp32core
