#pragma once
#include <cstdint>

// Standalone timing core used by both the Arduino-only probe and host tests.
// IO interface: level(), micros(), spin(), driveLowMs(ms), release().
namespace compi_probe {
enum class Verdict : uint8_t {
  Ok, LineLow, NoResponse, SyncIncomplete, FrameIncomplete, Checksum, Payload
};
inline const char* verdictName(Verdict v) {
  switch (v) {
    case Verdict::Ok: return "OK";
    case Verdict::LineLow: return "LINEA_EN_BAJO";
    case Verdict::NoResponse: return "SIN_RESPUESTA";
    case Verdict::SyncIncomplete: return "SINCRONIA_INCOMPLETA";
    case Verdict::FrameIncomplete: return "TRAMA_INCOMPLETA";
    case Verdict::Checksum: return "CHECKSUM";
    case Verdict::Payload: return "DATOS_INVALIDOS";
  }
  return "DESCONOCIDO";
}
struct Capture {
  uint8_t data[5] = {};
  uint16_t responseLowUs = 0;
  uint16_t responseHighUs = 0;
  uint16_t highUs[40] = {};
  uint8_t edges = 0;
  float humidity = 0;
  float temperature = 0;
  Verdict verdict = Verdict::NoResponse;
};
template <typename IO>
bool until(IO& io, bool level, uint32_t budgetUs) {
  const uint32_t begin = io.micros();
  while (io.level() != level) {
    if (static_cast<uint32_t>(io.micros() - begin) >= budgetUs) return false;
    io.spin();
  }
  return true;
}
template <typename IO>
bool width(IO& io, bool level, uint32_t timeoutUs, uint16_t& result) {
  if (io.level() != level) return false;
  const uint32_t from = io.micros();
  while (io.level() == level) {
    if (static_cast<uint32_t>(io.micros() - from) >= timeoutUs) return false;
    io.spin();
  }
  result = static_cast<uint16_t>(io.micros() - from);
  return true;
}
inline Verdict decode(Capture& c) {
  const uint8_t check = static_cast<uint8_t>(
      c.data[0] + c.data[1] + c.data[2] + c.data[3]);
  if (c.data[4] != check) return Verdict::Checksum;
  // DHT11 whole/decimal byte convention (DHT22 uses an incompatible layout).
  if (c.data[1] > 9 || c.data[3] > 9) return Verdict::Payload;
  c.humidity = c.data[0] + c.data[1] / 10.0f;
  c.temperature = c.data[2] + c.data[3] / 10.0f;
  if (c.humidity < 0 || c.humidity > 100 ||
      c.temperature < 0 || c.temperature > 60) return Verdict::Payload;
  return Verdict::Ok;
}
template <typename IO>
Capture sample(IO& io) {
  Capture c{};
  io.release();
  // The bus should idle HIGH. A LOW bus makes further stimulation unsafe.
  if (!io.level()) {
    c.verdict = Verdict::LineLow;
    return c;
  }
  io.driveLowMs(20); // DHT11 needs >=18ms
  io.release();       // open-drain: never drive HIGH against sensor
  if (!until(io, false, 180)) {
    c.verdict = Verdict::NoResponse;
    return c;
  }
  if (!width(io, false, 170, c.responseLowUs) ||
      !width(io, true, 170, c.responseHighUs)) {
    c.verdict = Verdict::SyncIncomplete;
    return c;
  }
  c.edges = 2;
  for (unsigned i = 0; i < 40; ++i) {
    // Low preamble ~50us, then 26-28us HIGH (0) or ~70us HIGH (1).
    if (!until(io, true, 150)) {
      c.verdict = Verdict::FrameIncomplete;
      return c;
    }
    uint16_t high = 0;
    if (!width(io, true, 150, high) || high < 15 || high > 115) {
      c.verdict = Verdict::FrameIncomplete;
      return c;
    }
    c.highUs[i] = high;
    c.edges = static_cast<uint8_t>(c.edges + 2);
    c.data[i / 8] = static_cast<uint8_t>(
        (c.data[i / 8] << 1) | (high >= 48 ? 1 : 0));
  }
  c.verdict = decode(c);
  return c;
}
} // namespace compi_probe
