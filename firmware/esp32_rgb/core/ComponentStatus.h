#pragma once

#include <cstddef>
#include <cstdint>
#include <cstdio>

#include "ComponentContract.h"

namespace esp32core {

// One authoritative runtime component report. The firmware is the final GPIO
// authority: [pins] are the exact pins currently leased by the module (empty
// when nothing was acquired), and [active] is true only while the module holds
// its lease and is ticking. A descriptor/entry never implies presence.
struct ComponentStatusEntry {
  const char* id;
  ComponentType type;
  HardwareInterface interfaceType;
  uint8_t pins[3];
  uint8_t pinCount;
  bool active;
  ComponentState state;
};

// Bounded, checksum-free JSON status. Returns false when it does not fit so the
// caller reports an explicit error instead of emitting a truncated document.
inline bool encodeComponentStatus(const ComponentStatusEntry* entries, size_t count,
                                  char* output, size_t capacity) {
  if (!output || capacity < 64) return false;
  int written = std::snprintf(
      output, capacity, "{\"version\":1,\"gpioAuthority\":\"firmware\",\"components\":[");
  if (written < 0 || static_cast<size_t>(written) >= capacity) return false;
  size_t used = static_cast<size_t>(written);
  for (size_t i = 0; i < count; ++i) {
    const ComponentStatusEntry& entry = entries[i];
    written = std::snprintf(
        output + used, capacity - used,
        "%s{\"id\":\"%s\",\"type\":\"%s\",\"interface\":\"%s\",\"state\":\"%s\","
        "\"active\":%s,\"pins\":[",
        i == 0 ? "" : ",", entry.id, componentTypeName(entry.type),
        interfaceName(entry.interfaceType), componentStateName(entry.state),
        entry.active ? "true" : "false");
    if (written < 0 || static_cast<size_t>(written) >= capacity - used) return false;
    used += static_cast<size_t>(written);
    const uint8_t pinCount = entry.pinCount > 3 ? 3 : entry.pinCount;
    for (uint8_t p = 0; p < pinCount; ++p) {
      written = std::snprintf(output + used, capacity - used, "%s%d",
                              p == 0 ? "" : ",", entry.pins[p]);
      if (written < 0 || static_cast<size_t>(written) >= capacity - used) return false;
      used += static_cast<size_t>(written);
    }
    written = std::snprintf(output + used, capacity - used, "]}");
    if (written < 0 || static_cast<size_t>(written) >= capacity - used) return false;
    used += static_cast<size_t>(written);
  }
  written = std::snprintf(output + used, capacity - used, "]}");
  if (written < 0 || static_cast<size_t>(written) >= capacity - used) return false;
  return true;
}
}  // namespace esp32core
