#include <cassert>
#include <cstdint>
#include <cstring>
#include <iostream>
#include "../firmware/esp32_rgb/core/RgbSnapshotCodec.h"

int main() {
  using esp32core::RgbSaved;
  using esp32core::RgbSnapshotCodec;
  RgbSaved expected{123, 45, 67, 210, 1, 2, 250};
  uint8_t encoded[RgbSnapshotCodec::kSize] = {};
  assert(RgbSnapshotCodec::encode(expected, encoded, sizeof(encoded)));
  RgbSaved restored{};
  assert(RgbSnapshotCodec::decode(encoded, sizeof(encoded), restored));
  assert(restored.red == 123 && restored.green == 45 && restored.blue == 67);
  assert(restored.brightness == 210 && restored.power == 1);
  assert(restored.mode == 2 && restored.speed == 250);
  // No output modification on corrupt or incomplete payload.
  restored = RgbSaved{};
  encoded[3] ^= 1;
  assert(!RgbSnapshotCodec::decode(encoded, sizeof(encoded), restored));
  assert(restored.red == 0 && restored.power == 0);
  encoded[3] ^= 1;
  assert(!RgbSnapshotCodec::decode(encoded, sizeof(encoded)-1, restored));
  encoded[0] = 99;
  assert(!RgbSnapshotCodec::decode(encoded, sizeof(encoded), restored));
  assert(!RgbSnapshotCodec::encode(RgbSaved{0,0,0,255,2,0,100},encoded,sizeof(encoded)));
  assert(!RgbSnapshotCodec::encode(RgbSaved{0,0,0,255,0,4,100},encoded,sizeof(encoded)));
  assert(!RgbSnapshotCodec::encode(RgbSaved{0,0,0,255,0,0,9},encoded,sizeof(encoded)));
  assert(!RgbSnapshotCodec::encode(expected,nullptr,sizeof(encoded)));
  std::cout << "PASS: independent versioned RGB snapshots, CRC corruption and fail-safe defaults\n";
}
