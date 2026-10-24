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
} // namespace esp32core
