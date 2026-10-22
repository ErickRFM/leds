#pragma once
#include <Arduino.h>
#include <Preferences.h>
#include <esp_system.h>
#include <cstring>
#include "DeviceIdentityCodec.h"

namespace esp32core {

// Dedicated NVS namespace: never erase or rewrite V3 lab credentials.
// Public stable ID is NOT proof of ownership and must never authorize commands.
class DeviceIdentity final {
 public:
  bool begin() {
    Preferences prefs;
    if (!prefs.begin("cc-v4-id", false)) return false;
    const String saved = prefs.getString("id", "");
    if (validDeviceId(saved.c_str())) {
      std::memcpy(id_, saved.c_str(), sizeof(id_));
      prefs.end();
      return true;
    }
    char candidate[17] = {};
    bool generated = false;
    for (int attempt = 0; attempt < 4 && !generated; ++attempt) {
      generated = formatDeviceId(esp_random(), esp_random(), candidate, sizeof(candidate));
    }
    if (!generated || prefs.putString("id", candidate) != 16 ||
        prefs.getString("id", "") != candidate) {
      prefs.end();
      return false; // Fail closed; do not rotate identity silently.
    }
    std::memcpy(id_, candidate, sizeof(id_));
    prefs.end();
    return true;
  }

  String value() const { return String(id_); }

 private:
  char id_[17] = {};
};
} // namespace esp32core
