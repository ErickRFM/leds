#pragma once

#include <cstdint>
#include <cstdlib>
#include <cstring>

namespace esp32core::crane {

enum class CommandType : uint8_t { Invalid, Get, Mode, Target, Axis, Stop, Home };
enum class ControlMode : uint8_t { Local, Remote };

struct ProtocolCommand {
  uint16_t requestId = 0;
  CommandType type = CommandType::Invalid;
  ControlMode mode = ControlMode::Local;
  bool baseAxis = true;
  float baseDeg = 90.0f;
  float armDeg = 90.0f;
  float angleDeg = 90.0f;
  float speedDegPerSecond = 45.0f;
};

inline bool parseUnsigned16(const char* value, uint16_t& out) {
  if (!value || !*value) return false;
  unsigned long parsed = 0;
  for (const char* p = value; *p; ++p) {
    if (*p < '0' || *p > '9') return false;
    parsed = parsed * 10UL + static_cast<unsigned long>(*p - '0');
    if (parsed > 65535UL) return false;
  }
  if (parsed == 0) return false;
  out = static_cast<uint16_t>(parsed);
  return true;
}

inline bool parseFloatStrict(const char* value, float& out) {
  if (!value || !*value) return false;
  char* end = nullptr;
  const float parsed = std::strtof(value, &end);
  if (end == value || !end || *end != '\0' ||
      parsed != parsed || parsed > 1000000.0f || parsed < -1000000.0f) return false;
  out = parsed;
  return true;
}

inline bool parseCraneCommand(const char* input, ProtocolCommand& out) {
  out = {};
  if (!input) return false;
  const size_t length = std::strlen(input);
  if (length < 7 || length > 64) return false;
  char buffer[65] = {};
  std::memcpy(buffer, input, length);
  char* save = nullptr;
  char* token = std::strtok_r(buffer, ",", &save);
  if (!token || std::strcmp(token, "CRN") != 0) return false;
  token = std::strtok_r(nullptr, ",", &save);
  if (!parseUnsigned16(token, out.requestId)) return false;
  token = std::strtok_r(nullptr, ",", &save);
  if (!token) return false;

  if (std::strcmp(token, "GET") == 0) {
    out.type = CommandType::Get;
  } else if (std::strcmp(token, "STOP") == 0) {
    out.type = CommandType::Stop;
  } else if (std::strcmp(token, "HOME") == 0) {
    out.type = CommandType::Home;
  } else if (std::strcmp(token, "MODE") == 0) {
    token = std::strtok_r(nullptr, ",", &save);
    if (!token || std::strtok_r(nullptr, ",", &save)) return false;
    if (std::strcmp(token, "LOCAL") == 0) out.mode = ControlMode::Local;
    else if (std::strcmp(token, "REMOTE") == 0) out.mode = ControlMode::Remote;
    else return false;
    out.type = CommandType::Mode;
  } else if (std::strcmp(token, "TARGET") == 0) {
    char* base = std::strtok_r(nullptr, ",", &save);
    char* arm = std::strtok_r(nullptr, ",", &save);
    char* speed = std::strtok_r(nullptr, ",", &save);
    if (std::strtok_r(nullptr, ",", &save) ||
        !parseFloatStrict(base, out.baseDeg) ||
        !parseFloatStrict(arm, out.armDeg) ||
        !parseFloatStrict(speed, out.speedDegPerSecond) ||
        out.baseDeg < 0.0f || out.baseDeg > 180.0f ||
        out.armDeg < 0.0f || out.armDeg > 180.0f ||
        out.speedDegPerSecond < 1.0f || out.speedDegPerSecond > 180.0f) return false;
    out.type = CommandType::Target;
  } else if (std::strcmp(token, "AXIS") == 0) {
    char* axis = std::strtok_r(nullptr, ",", &save);
    char* angle = std::strtok_r(nullptr, ",", &save);
    char* speed = std::strtok_r(nullptr, ",", &save);
    if (std::strtok_r(nullptr, ",", &save) || !axis ||
        !parseFloatStrict(angle, out.angleDeg) ||
        !parseFloatStrict(speed, out.speedDegPerSecond) ||
        out.angleDeg < 0.0f || out.angleDeg > 180.0f ||
        out.speedDegPerSecond < 1.0f || out.speedDegPerSecond > 180.0f) return false;
    if (std::strcmp(axis, "BASE") == 0) out.baseAxis = true;
    else if (std::strcmp(axis, "ARM") == 0) out.baseAxis = false;
    else return false;
    out.type = CommandType::Axis;
  } else {
    return false;
  }

  // No unconsumed fields are accepted for simple commands.
  if (out.type == CommandType::Get || out.type == CommandType::Stop ||
      out.type == CommandType::Home) {
    if (std::strtok_r(nullptr, ",", &save)) return false;
  }
  return true;
}

inline const char* controlModeName(ControlMode mode) {
  return mode == ControlMode::Remote ? "REMOTE" : "LOCAL";
}

}  // namespace esp32core::crane
