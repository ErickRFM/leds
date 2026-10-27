#pragma once
#include <cstddef>
#include <cstdint>
#include <cstring>

namespace esp32core {

// Version 1 owner-only LDR calibration command. Does not authorize execution.
struct BleLdrCalibration {
  uint16_t requestId = 0;
  uint16_t darkRaw = 0;
  uint16_t brightRaw = 0;
};

inline bool parseBleLdrCalibration(const char* input, BleLdrCalibration& out) {
  if (!input || std::strncmp(input, "LDR,", 4) != 0) return false;
  const char* p = input + 4;
  uint32_t values[3] = {};
  for (uint8_t i = 0; i < 3; ++i) {
    size_t count = 0;
    while (*p >= '0' && *p <= '9') {
      if (++count > 5) return false;
      values[i] = values[i] * 10u + static_cast<uint32_t>(*p - '0');
      ++p;
    }
    if (!count || (i < 2 && *p != ',') || (i == 2 && *p != '\0')) return false;
    if (i < 2) ++p;
  }
  if (values[0] == 0 || values[0] > 65535u ||
      values[1] > 4095u || values[2] > 4095u ||
      (values[2] > values[1] ? values[2] - values[1] :
       values[1] - values[2]) < 32u) return false;
  out = {static_cast<uint16_t>(values[0]), static_cast<uint16_t>(values[1]),
         static_cast<uint16_t>(values[2])};
  return true;
}

}  // namespace esp32core
