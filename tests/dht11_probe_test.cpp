// Real isolated-probe timing code against deterministic DHT11 waveforms.
// No ESP32, Wi-Fi, BLE, Android, sensors or external libraries.
#include <cassert>
#include <cstdint>
#include <iostream>
#include <vector>
#include "../tools/dht11_probe/DhtProbeCore.h"

using compi_probe::Capture;
using compi_probe::Verdict;
struct Segment { bool high; unsigned us; };
struct Fake {
  std::vector<Segment> waveform;
  bool stuckLow = false;
  uint32_t tick = 0;
  bool started = false;
  bool maskSupported = true;
  bool masked = false;
  bool injectPause = false;
  bool pauseInjected = false;
  void beginCapture() { masked = maskSupported; }
  void endCapture() { masked = false; }
  bool level() const {
    if (stuckLow) return false;
    if (!started) return true;
    uint32_t t = tick;
    for (const auto& seg : waveform) {
      if (t < seg.us) return seg.high;
      t -= seg.us;
    }
    return true;
  }
  uint32_t micros() const { return tick; }
  void spin() {
    ++tick;
    // Model a 200us pre-emption during bit capture on a busy ESP32 core.
    if (injectPause && !masked && !pauseInjected && tick > 710) {
      tick += 200;
      pauseInjected = true;
    }
  }
  void driveLowMs(uint16_t) { /* time origin begins after release */ }
  void release() { started = true; }
};
Fake waveform(const uint8_t (&bytes)[5]) {
  Fake io;
  io.waveform.push_back({true, 32});
  io.waveform.push_back({false, 80});
  io.waveform.push_back({true, 80});
  for (unsigned i = 0; i < 40; ++i) {
    io.waveform.push_back({false, 50});
    const bool bit = ((bytes[i / 8] >> (7 - i % 8)) & 1) != 0;
    io.waveform.push_back({true, bit ? 70u : 27u});
  }
  io.waveform.push_back({false, 50});
  io.waveform.push_back({true, 200});
  return io;
}
int main() {
  const uint8_t ok[5] = {55, 0, 24, 0, 79};
  Fake success = waveform(ok);
  const Capture c = compi_probe::sample(success);
  assert(c.verdict == Verdict::Ok);
  assert(c.edges == 82 && c.humidity == 55.0f && c.temperature == 24.0f);
  assert(c.data[0] == 55 && c.data[4] == 79);
  assert(c.responseLowUs == 80 && c.responseHighUs == 80);
  // Exactly the 5 bytes measured on the user's DHT11 GPIO16 probe.
  const uint8_t physical[5] = {0x3B, 0x00, 0x15, 0x02, 0x52};
  Fake actual = waveform(physical);
  const auto verified = compi_probe::sample(actual);
  assert(verified.verdict == Verdict::Ok);
  assert(verified.humidity == 59.0f && verified.temperature == 21.2f);
  Fake withMask = waveform(physical);
  withMask.injectPause = true;
  assert(compi_probe::sample(withMask).verdict == Verdict::Ok);
  Fake withoutMask = waveform(physical);
  withoutMask.maskSupported = false;
  withoutMask.injectPause = true;
  assert(compi_probe::sample(withoutMask).verdict != Verdict::Ok);
  assert(withoutMask.pauseInjected);
  assert(!withMask.masked && !withoutMask.masked);
  Fake idle;
  assert(compi_probe::sample(idle).verdict == Verdict::NoResponse);
  Fake low;
  low.stuckLow = true;
  assert(compi_probe::sample(low).verdict == Verdict::LineLow);
  const uint8_t bad[5] = {55, 0, 24, 0, 78};
  Fake checksum = waveform(bad);
  assert(compi_probe::sample(checksum).verdict == Verdict::Checksum);
  Fake shortFrame = waveform(ok);
  shortFrame.waveform.resize(9);
  assert(compi_probe::sample(shortFrame).verdict == Verdict::FrameIncomplete);
  Fake corrupt = waveform(ok);
  corrupt.waveform[4].us = 200;
  assert(compi_probe::sample(corrupt).verdict == Verdict::FrameIncomplete);
  std::cout << "dht11_probe_test: passed 10 isolated waveform cases including bounded interrupt guard\n";
}
