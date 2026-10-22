#pragma once
#include <cstdint>

namespace esp32core {

// Firmware GPIO/one-wire evidence, NOT proof that a sensor is physically present.
enum class DhtWireFault : uint8_t {
  NotAttempted, Ok, LineHeldLow, NoResponse, ResponseTimeout,
  BitTimeout, BadChecksum, InvalidPayload
};

inline const char* dhtWireFaultName(DhtWireFault reason) {
  switch (reason) {
    case DhtWireFault::NotAttempted: return "not_attempted";
    case DhtWireFault::Ok: return "ok";
    case DhtWireFault::LineHeldLow: return "line_low";
    case DhtWireFault::NoResponse: return "no_response";
    case DhtWireFault::ResponseTimeout: return "response_timeout";
    case DhtWireFault::BitTimeout: return "bit_timeout";
    case DhtWireFault::BadChecksum: return "bad_checksum";
    case DhtWireFault::InvalidPayload: return "invalid_payload";
  }
  return "unknown";
}

// Pulse widths: 0 ~26-28 us, 1 ~70 us. Reject unsupported timings.
inline bool dhtDecodeHighPulse(uint32_t durationUs, bool& one) {
  if (durationUs < 15 || durationUs > 115) return false;
  one = durationUs >= 48;
  return true;
}
}
