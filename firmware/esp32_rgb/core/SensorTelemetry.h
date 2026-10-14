#pragma once

#include <cstddef>
#include <cstdint>

namespace esp32core {

enum class MeasurementType : uint8_t {
  None,
  Humidity,
  Temperature,
  SoilMoisture,
  AdcRaw,
  RgbState,
};

// Status is deliberately separate from the numeric value. A consumer must never
// treat value==0 as "no data": it reads status first. Fresh means produced by a
// successful device read within the module's freshness window.
enum class MeasurementStatus : uint8_t {
  Absent,         // No successful read has ever happened.
  Fresh,          // Valid read within the freshness window.
  Stale,          // Last read is too old; the value must not drive automation.
  Invalid,        // Read happened but the payload failed validation.
  NotCalibrated,  // Raw value exists but cannot be converted to physical units.
  SensorError,    // Repeated failures; the sensor is considered faulty.
};

struct Measurement {
  MeasurementType type = MeasurementType::None;
  MeasurementStatus status = MeasurementStatus::Absent;
  bool valid = false;
  float value = 0.0f;
  const char* unit = "";
  uint32_t timestampMs = 0;
};

constexpr const char* measurementTypeName(MeasurementType type) {
  switch (type) {
    case MeasurementType::None: return "none";
    case MeasurementType::Humidity: return "humidity";
    case MeasurementType::Temperature: return "temperature";
    case MeasurementType::SoilMoisture: return "soil_moisture";
    case MeasurementType::AdcRaw: return "adc_raw";
    case MeasurementType::RgbState: return "rgb_state";
  }
  return "none";
}

constexpr const char* measurementStatusName(MeasurementStatus status) {
  switch (status) {
    case MeasurementStatus::Absent: return "absent";
    case MeasurementStatus::Fresh: return "fresh";
    case MeasurementStatus::Stale: return "stale";
    case MeasurementStatus::Invalid: return "invalid";
    case MeasurementStatus::NotCalibrated: return "not_calibrated";
    case MeasurementStatus::SensorError: return "sensor_error";
  }
  return "absent";
}
}  // namespace esp32core
