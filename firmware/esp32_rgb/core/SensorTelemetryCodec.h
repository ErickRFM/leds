#pragma once

#include <cstddef>
#include <cstdio>
#include <cstring>
#include "SensorTelemetry.h"

namespace esp32core {

// Bounded JSON encoder for read-only telemetry. It never allocates, never
// truncates silently (returns false when the buffer is too small) and never
// emits NaN/Inf: such values are downgraded to an invalid measurement.
struct SensorTelemetryCodec {
  static constexpr size_t kMaxEntries = 12;

  static bool finite(float value) {
    return value == value && value > -1.0e30f && value < 1.0e30f;
  }

  static bool encode(const Measurement* entries, size_t count,
                     char* output, size_t capacity) {
    if (!output || capacity < 32 || (count > 0 && entries == nullptr)) return false;
    if (count > kMaxEntries) count = kMaxEntries;
    size_t used = 0;
    int written = std::snprintf(output, capacity, "{\"version\":1,\"measurements\":[");
    if (written < 0 || static_cast<size_t>(written) >= capacity) return false;
    used = static_cast<size_t>(written);
    for (size_t i = 0; i < count; ++i) {
      const Measurement& m = entries[i];
      const bool valid = m.valid && finite(m.value);
      written = std::snprintf(
          output + used, capacity - used,
          "%s{\"type\":\"%s\",\"status\":\"%s\",\"valid\":%s,\"value\":%.2f,"
          "\"unit\":\"%s\",\"timestampMs\":%lu}",
          i == 0 ? "" : ",", measurementTypeName(m.type),
          measurementStatusName(valid ? m.status : MeasurementStatus::Invalid),
          valid ? "true" : "false", valid ? static_cast<double>(m.value) : 0.0,
          m.unit ? m.unit : "", static_cast<unsigned long>(m.timestampMs));
      if (written < 0 || static_cast<size_t>(written) >= capacity - used) return false;
      used += static_cast<size_t>(written);
    }
    written = std::snprintf(output + used, capacity - used, "]}");
    if (written < 0 || static_cast<size_t>(written) >= capacity - used) return false;
    return true;
  }
};
}  // namespace esp32core
