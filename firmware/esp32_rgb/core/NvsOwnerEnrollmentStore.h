#pragma once

// NVS-backed OwnerEnrollmentStore. Compiled only for the ESP32 target; host
// builds use an in-memory store. The whole snapshot is written as one blob so
// a partially written record cannot be mistaken for a valid owner.
//
// At-rest protection is provided by ESP32 flash encryption (enable it in
// production): the private key must never be readable from the transport.

#if defined(ARDUINO) || defined(ESP_PLATFORM)

#include <Preferences.h>
#include <cstring>

#include "OwnerEnrollmentStore.h"

namespace esp32core {

class NvsOwnerEnrollmentStore final : public OwnerEnrollmentStore {
 public:
  static constexpr const char* kNamespace = "compi_owner";
  static constexpr const char* kKey = "record";
  static constexpr uint32_t kMagic = 0x434F4D50u;  // "COMP"
  static constexpr uint16_t kVersion = 1;

  bool load(OwnerEnrollmentSnapshot& out) override {
    out = OwnerEnrollmentSnapshot{};
    Preferences preferences;
    // Preferences.begin(readOnly=true) fails for a namespace that does not
    // exist yet (ESP_ERR_NVS_NOT_FOUND). That made a fresh install halt in
    // setup() *before* the Wi-Fi SoftAP and BLE were started. Opening in
    // read/write mode creates only the namespace; no owner is enrolled and
    // load still rejects malformed or unreadable existing records.
    if (!preferences.begin(kNamespace, false)) return false;
    const size_t length = preferences.getBytesLength(kKey);
    if (length == 0) {
      preferences.end();
      return true;  // no record yet: a fresh board, not a failure
    }
    bool ok = false;
    if (length == sizeof(StoredRecord)) {
      StoredRecord record;
      if (preferences.getBytes(kKey, &record, sizeof(record)) == sizeof(record) &&
          record.magic == kMagic && record.version == kVersion) {
        out = record.snapshot;
        ok = true;
      }
    }
    preferences.end();
    // An unknown/corrupt record fails closed: the caller keeps the transport
    // disabled rather than trusting half-read owner state.
    return ok;
  }

  bool save(const OwnerEnrollmentSnapshot& snapshot) override {
    Preferences preferences;
    if (!preferences.begin(kNamespace, false)) return false;
    StoredRecord record;
    record.magic = kMagic;
    record.version = kVersion;
    record.snapshot = snapshot;
    const size_t written = preferences.putBytes(kKey, &record, sizeof(record));
    preferences.end();
    return written == sizeof(record);
  }

  bool clear() override {
    Preferences preferences;
    if (!preferences.begin(kNamespace, false)) return false;
    const bool removed = preferences.remove(kKey);
    const bool gone = preferences.getBytesLength(kKey) == 0;
    preferences.end();
    return removed || gone;
  }

 private:
  struct StoredRecord {
    uint32_t magic;
    uint16_t version;
    OwnerEnrollmentSnapshot snapshot;
  };
};

}  // namespace esp32core

#endif  // ARDUINO || ESP_PLATFORM
