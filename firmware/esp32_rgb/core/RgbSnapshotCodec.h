#pragma once
#include <cstddef>
#include <cstdint>

namespace esp32core {

// Small versioned, checksummed snapshot; never contains network or pairing secrets.
struct RgbSaved {
  uint8_t red = 0, green = 0, blue = 0, brightness = 255, power = 0, mode = 0;
  uint16_t speed = 100;
};

class RgbSnapshotCodec final {
 public:
  static constexpr size_t kSize = 11;
  static constexpr uint8_t kVersion = 1;

  static bool valid(const RgbSaved& value) {
    return value.power <= 1 && value.mode <= 3 &&
           value.speed >= 10 && value.speed <= 1000;
  }
  static uint16_t checksum(const uint8_t* data, size_t size) {
    uint16_t crc = 0xFFFF;
    for (size_t i = 0; i < size; ++i) {
      crc ^= static_cast<uint16_t>(data[i]) << 8;
      for (int bit = 0; bit < 8; ++bit) {
        crc = (crc & 0x8000) ? static_cast<uint16_t>((crc << 1) ^ 0x1021) :
            static_cast<uint16_t>(crc << 1);
      }
    }
    return crc;
  }
  static bool encode(const RgbSaved& value, uint8_t* out, size_t size) {
    if (!out || size != kSize || !valid(value)) return false;
    out[0] = kVersion;
    out[1] = value.red; out[2] = value.green; out[3] = value.blue;
    out[4] = value.brightness; out[5] = value.power; out[6] = value.mode;
    out[7] = static_cast<uint8_t>(value.speed & 0xFF);
    out[8] = static_cast<uint8_t>(value.speed >> 8);
    const uint16_t crc = checksum(out, 9);
    out[9] = static_cast<uint8_t>(crc & 0xFF);
    out[10] = static_cast<uint8_t>(crc >> 8);
    return true;
  }
  static bool decode(const uint8_t* input, size_t size, RgbSaved& out) {
    if (!input || size != kSize || input[0] != kVersion) return false;
    const uint16_t stored = static_cast<uint16_t>(input[9]) |
        static_cast<uint16_t>(input[10]) << 8;
    if (stored != checksum(input, 9)) return false;
    RgbSaved candidate{};
    candidate.red = input[1]; candidate.green = input[2]; candidate.blue = input[3];
    candidate.brightness = input[4]; candidate.power = input[5];
    candidate.mode = input[6];
    candidate.speed = static_cast<uint16_t>(input[7]) |
        static_cast<uint16_t>(input[8]) << 8;
    if (!valid(candidate)) return false;
    out = candidate;
    return true;
  }
};
} // namespace esp32core
