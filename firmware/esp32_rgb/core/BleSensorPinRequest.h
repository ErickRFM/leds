#pragma once
#include <cstddef>
#include <cstdint>
#include <cstring>

namespace esp32core {

// Deliberately bounded to the user's single-pin DHT11 and LDR modules.
// The parser never authorizes GPIO: owner authentication + CAS remain required.
struct BleSensorPinRequest {
  char token[9] = {};
  const char* component = nullptr;
  uint8_t from = 0;
  uint8_t to = 0;
};

inline bool parseBleSensorPinRequest(const char* frame, BleSensorPinRequest& out) {
  if (!frame || std::strncmp(frame, "PIN,", 4) != 0) return false;
  const char* p = frame + 4;
  BleSensorPinRequest parsed{};
  for (uint8_t i = 0; i < 8; ++i) {
    const char c = p[i];
    if (!((c >= '0' && c <= '9') ||
          (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) return false;
    parsed.token[i] = c;
  }
  if (p[8] != ',' || p[10] != ',') return false;
  parsed.component = p[9] == 'D' ? "dht11" :
                     p[9] == 'L' ? "ldr_light" : nullptr;
  if (!parsed.component) return false;
  p += 11;
  uint32_t values[2] = {};
  for (uint8_t i = 0; i < 2; ++i) {
    uint8_t length = 0;
    while (*p >= '0' && *p <= '9') {
      if (++length > 2) return false;
      values[i] = values[i] * 10u + static_cast<uint32_t>(*p - '0');
      ++p;
    }
    if (!length || (i == 0 ? *p != ',' : *p != '\0')) return false;
    if (i == 0) ++p;
    if (values[i] > 39u) return false;
  }
  parsed.from = static_cast<uint8_t>(values[0]);
  parsed.to = static_cast<uint8_t>(values[1]);
  out = parsed;
  return true;
}

}  // namespace esp32core
