#pragma once
#include <cstdint>
#include <cstddef>
#include <cstdio>

namespace esp32core {

// Public, NON-secret identity. Not a pairing code, MAC, proof of possession,
// authentication token or permission to control any GPIO.
inline bool validDeviceId(const char* id) {
  if (!id) return false;
  for (size_t i = 0; i < 16; ++i) {
    const char c = id[i];
    if (!c || !((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F'))) return false;
  }
  return id[16] == '\0';
}

inline bool formatDeviceId(uint32_t upper, uint32_t lower, char* buffer, size_t capacity) {
  if (!buffer || capacity < 17 || (upper == 0 && lower == 0)) return false;
  const int written = std::snprintf(buffer, capacity, "%08lX%08lX",
      static_cast<unsigned long>(upper), static_cast<unsigned long>(lower));
  return written == 16 && validDeviceId(buffer);
}

} // namespace esp32core
