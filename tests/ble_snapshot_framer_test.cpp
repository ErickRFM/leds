#include <cassert>
#include <cstring>
#include <string>
#include <vector>
#include <iostream>
#include "../firmware/esp32_rgb/core/BleSnapshotFramer.h"
#include "../firmware/esp32_rgb/core/CompiCommandArbiter.h"

using namespace esp32core;

void capture(const char* frame, void* ctx) {
  auto* frames = static_cast<std::vector<std::string>*>(ctx);
  frames->emplace_back(frame);
  assert(frames->back().size() <= 61);
}

int main() {
  BleSnapshotFramer source;
  char kind = 0;
  uint16_t seq = 0;
  for (const char* frame : {"", "SNP,S", "SNP,S,", "SNP,S,0", "SNP,S,65536",
                             "SNP,S,1\n", "SNP,X,2", "SNP,T,-1",
                             "SNP,T,123456", "SNP,T,1extra"}) {
    assert(!parseBleSnapshotRequest(frame, kind, seq));
  }
  assert(parseBleSnapshotRequest("SNP,S,65535", kind, seq));
  assert(kind == 'S' && seq == 65535);
  assert(parseBleSnapshotRequest("SNP,T,23", kind, seq));
  assert(kind == 'T' && seq == 23);
  assert(!parseBleSnapshotRequest(nullptr, kind, seq));

  std::string json = "{\"version\":1,\"measurements\":[";
  for (int i = 0; i < 8; ++i) {
    json += (i ? "," : "") + std::string(
        "{\"type\":\"temperature\",\"status\":\"fresh\",\"valid\":true,\"value\":21.2}");
  }
  json += "]}";
  std::vector<std::string> frames;
  assert(source.start(BleSnapshotFramer::Kind::Telemetry, json.c_str(), 23));
  assert(!source.start(BleSnapshotFramer::Kind::Status, "{}", 24));
  while (source.active()) assert(source.tick(capture, &frames));
  assert(frames.size() >= 3);
  assert(frames.front().rfind("B,T,23,", 0) == 0);
  assert(frames.back() == "E,T,23");
  std::string rebuilt;
  for (size_t i = 1; i + 1 < frames.size(); ++i) {
    const size_t comma = frames[i].find(',', 2);
    const size_t next = frames[i].find(',', comma+1);
    const size_t last = frames[i].find(',', next+1);
    assert(last != std::string::npos);
    rebuilt += frames[i].substr(last+1);
  }
  assert(rebuilt == json);
  assert(!source.active());
  assert(!source.start(BleSnapshotFramer::Kind::Telemetry, "", 3));
  std::string longJson(BleSnapshotFramer::kPayloadMax + 2, 'a');
  assert(!source.start(BleSnapshotFramer::Kind::Status, longJson.c_str(), 2));

  // Even a correctly framed request MUST be rejected by the owner arbiter.
  CompiCommandArbiter arbiter;
  auto sourceType = CompiCommandArbiter::mapBleSource(
      BleAuthState::CONNECTED_UNAUTHENTICATED);
  assert(!arbiter.evaluate(sourceType, false).allowed);
  assert(arbiter.evaluate(CompiCommandArbiter::mapBleSource(
      BleAuthState::AUTHORIZED), false).allowed);
  std::cout << "BLE snapshots and owner gate: ALL OK\n";
  return 0;
}
