#pragma once
#include <cstdint>

namespace esp32core {

// DHT11 and AM2302/DHT22 share a 40-bit pulse frame but use different
// wake-up timing and payload layouts. Do not infer the sensor model from a
// coincidental checksum. A bad frame never becomes valid telemetry.
enum class DhtModel : uint8_t { DHT11, DHT22 };
struct DhtReading { bool ok; float humidity; float temperature; };

inline DhtReading decodeDhtFrame(const uint8_t (&data)[5], DhtModel model) {
  const auto sum = static_cast<uint8_t>(data[0] + data[1] + data[2] + data[3]);
  if (sum != data[4]) return {false, 0.0f, 0.0f};
  if (model == DhtModel::DHT11) {
    // DHT11 has integer and decimal byte pairs. Most modules send 0
    // decimals; reject non-decimal payloads rather than treating them as DHT22.
    if (data[1] > 9 || data[3] > 9) return {false, 0.0f, 0.0f};
    const float humidity = data[0] + data[1] / 10.0f;
    const float temperature = data[2] + data[3] / 10.0f;
    if (humidity < 0.0f || humidity > 100.0f ||
        temperature < 0.0f || temperature > 60.0f) return {false, 0.0f, 0.0f};
    return {true, humidity, temperature};
  }
  const uint16_t rh = (static_cast<uint16_t>(data[0]) << 8) | data[1];
  const uint16_t magnitude = (static_cast<uint16_t>(data[2] & 0x7f) << 8) | data[3];
  const float humidity = rh / 10.0f;
  const float temperature = magnitude / 10.0f * ((data[2] & 0x80) ? -1.0f : 1.0f);
  if (humidity > 100.0f || temperature < -40.0f || temperature > 85.0f)
    return {false, 0.0f, 0.0f};
  return {true, humidity, temperature};
}
} // namespace esp32core
