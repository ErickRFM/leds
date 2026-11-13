#pragma once
#include <cstddef>
#include <cstdint>
#include <cstring>

namespace esp32core {

// Only single-pin DHT11 and LDR assignments. The parser never authorizes a
// GPIO change; channel authorization, runtime ownership and CAS still apply.
struct BleSensorPinRequest {
  char token[17] = {};
  const char* component = nullptr;
  uint8_t from = 0;
  uint8_t to = 0;
};

inline bool parseBleSensorPinRequest(const char* frame, BleSensorPinRequest& out) {
  if (!frame) return false;
  size_t length = 0;
  while (length <= 28 && frame[length]) ++length;
  if (length < 26 || length > 28 || std::strncmp(frame,"PIN,",4)) return false;
  BleSensorPinRequest parsed{};
  for (size_t i = 0; i < 16; ++i) {
    const char c = frame[4+i];
    if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') ||
          (c >= 'A' && c <= 'F'))) return false;
    parsed.token[i] = c;
  }
  if (frame[20] != ',' || frame[22] != ',') return false;
  parsed.component = frame[21] == 'D' ? "dht11" :
      frame[21] == 'L' ? "ldr_light" : nullptr;
  if (!parsed.component) return false;
  const char* p = frame + 23;
  uint8_t pins[2]{};
  for (size_t i = 0; i < 2; ++i) {
    unsigned value = 0, digits = 0;
    while (*p >= '0' && *p <= '9') {
      if (++digits > 2) return false;
      value = value * 10u + static_cast<unsigned>(*p++ - '0');
    }
    if (!digits || value > 39 || (i == 0 ? *p != ',' : *p != '\0')) return false;
    pins[i] = static_cast<uint8_t>(value);
    if (i == 0) ++p;
  }
  parsed.from = pins[0]; parsed.to = pins[1]; out = parsed;
  return true;
}

// Multi-signal component rewiring over the owner BLE channel. Frame grammar:
//   CPN,<16 hex>,<C>,<14 two-digit pins>
// 'C' selects robotics_crane; the digits are seven fixed-order from/to pairs
// (servo base, servo arm, joystick up/down/left/right/mid), zero meaning
// "previously inactive". Fixed-width keeps the frame within one 64-byte
// characteristic write, and the same CAS + readback contract as HTTP applies.
struct BleComponentPinRequest {
  char token[17] = {};
  const char* component = nullptr;
  uint8_t from[7] = {};
  uint8_t to[7] = {};
};

inline bool parseBleComponentPinRequest(const char* frame, BleComponentPinRequest& out) {
  if (!frame) return false;
  size_t length = 0;
  while (frame[length]) ++length;
  if (length != 51 || std::strncmp(frame, "CPN,", 4)) return false;
  BleComponentPinRequest parsed{};
  for (size_t i = 0; i < 16; ++i) {
    const char c = frame[4 + i];
    if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') ||
          (c >= 'A' && c <= 'F'))) return false;
    parsed.token[i] = c;
  }
  if (frame[20] != ',' || frame[22] != ',') return false;
  parsed.component = frame[21] == 'C' ? "robotics_crane" : nullptr;
  if (!parsed.component) return false;
  const char* p = frame + 23;
  for (size_t i = 0; i < 7; ++i) {
    for (size_t j = 0; j < 2; ++j) {
      unsigned value = 0;
      for (size_t k = 0; k < 2; ++k, ++p) {
        if (*p < '0' || *p > '9') return false;
        value = value * 10u + static_cast<unsigned>(*p - '0');
      }
      if (value > 39) return false;
      if (j == 0) parsed.from[i] = static_cast<uint8_t>(value);
      else parsed.to[i] = static_cast<uint8_t>(value);
    }
  }
  if (*p != '\0') return false;
  out = parsed;
  return true;
}
} // namespace esp32core
