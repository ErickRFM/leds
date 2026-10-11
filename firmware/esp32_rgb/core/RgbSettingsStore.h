#pragma once
#include <Arduino.h>
#include <Preferences.h>
#include "RgbSnapshotCodec.h"

namespace esp32core {

// One independent NVS blob avoids half-updated RGB fields and avoids
// rewriting legacy credentials. No secrets are stored here.
class RgbSettingsStore final {
 public:
  bool load(RgbSaved& out) {
    Preferences prefs;
    if (!prefs.begin("cc-v4-rgb", true)) return false;
    uint8_t bytes[RgbSnapshotCodec::kSize] = {};
    const size_t read = prefs.getBytes("state", bytes, sizeof(bytes));
    prefs.end();
    return RgbSnapshotCodec::decode(bytes, read, out);
  }
  bool save(const RgbSaved& value) {
    uint8_t bytes[RgbSnapshotCodec::kSize] = {};
    if (!RgbSnapshotCodec::encode(value, bytes, sizeof(bytes))) return false;
    Preferences prefs;
    if (!prefs.begin("cc-v4-rgb", false)) return false;
    const size_t written = prefs.putBytes("state", bytes, sizeof(bytes));
    prefs.end();
    return written == sizeof(bytes);
  }
};
} // namespace esp32core
