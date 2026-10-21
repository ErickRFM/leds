#pragma once
#include <cstring>

namespace esp32core {

// Exact, bounded extension of the existing RGB GATT write characteristic.
// Parses no arbitrary GPIO, pairing or component-control frames.
enum class BleEnvironmentalAction { Invalid, Get, Start, Stop };

inline BleEnvironmentalAction parseBleEnvironmentalCommand(const char* line) {
  if (line == nullptr) return BleEnvironmentalAction::Invalid;
  if (std::strcmp(line, "ENV,GET") == 0) return BleEnvironmentalAction::Get;
  if (std::strcmp(line, "ENV,START") == 0) return BleEnvironmentalAction::Start;
  if (std::strcmp(line, "ENV,STOP") == 0) return BleEnvironmentalAction::Stop;
  return BleEnvironmentalAction::Invalid;
}

inline bool isBleEnvironmentalCommand(const char* line) {
  return parseBleEnvironmentalCommand(line) != BleEnvironmentalAction::Invalid;
}

}  // namespace esp32core
