#pragma once
#include <cstring>
#include <cstdio>
#include "EnvironmentalRgbPolicy.h"

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

inline bool encodeBleEnvironmentalStatus(const EnvironmentalRgbSnapshot& snapshot, char* output, size_t capacity) {
  if (output == nullptr || capacity < 64) return false;
  snprintf(output, capacity, "ENV,STATE,%s,%s,%s,%s,%u,%u,%u,%u,%s,%s",
           snapshot.enabled ? "ON" : "OFF",
           environmentalRgbStateName(snapshot.state),
           snapshot.reason != nullptr ? snapshot.reason : "UNKNOWN",
           environmentalRgbBandName(snapshot.band),
           static_cast<unsigned>(snapshot.red),
           static_cast<unsigned>(snapshot.green),
           static_cast<unsigned>(snapshot.blue),
           static_cast<unsigned>(snapshot.brightness),
           snapshot.humidityUsed ? "1" : "0",
           snapshot.lightUsed ? "1" : "0");
  return true;
}

}  // namespace esp32core
