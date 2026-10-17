// Reproduces fresh ESP32 flash where "compi_owner" does not exist in NVS.
// The old read-only Preferences.begin() path failed before wifiLab.begin().
#include <cassert>
#include <cstdint>
#include <cstring>
#include <iostream>
#include "stubs/Preferences.h"
#include "../firmware/esp32_rgb/core/NvsOwnerEnrollmentStore.h"

using esp32core::NvsOwnerEnrollmentStore;
using esp32core::OwnerEnrollmentSnapshot;

int main() {
  Preferences::reset();
  NvsOwnerEnrollmentStore store;
  OwnerEnrollmentSnapshot blank{};
  // First boot must open/create an empty namespace WITHOUT creating an owner.
  assert(!Preferences::exists(NvsOwnerEnrollmentStore::kNamespace));
  assert(store.load(blank));
  assert(Preferences::exists(NvsOwnerEnrollmentStore::kNamespace));
  assert(!blank.enrolled);
  assert(!blank.hasDeviceKey);

  // Identity/key material can be persisted, then read back after a reboot.
  OwnerEnrollmentSnapshot expected{};
  expected.hasDeviceKey = true;
  expected.enrolled = true;
  expected.lastCounter = 17;
  expected.devicePrivateKey[0] = 0xA7;
  expected.devicePublicKey[0] = 0xC8;
  expected.ownerPublicKey[0] = 0x53;
  std::strcpy(expected.ownerId, "lab-owner");
  assert(store.save(expected));
  NvsOwnerEnrollmentStore rebooted;
  OwnerEnrollmentSnapshot got{};
  assert(rebooted.load(got));
  assert(got.hasDeviceKey && got.enrolled);
  assert(got.lastCounter == 17);
  assert(got.devicePrivateKey[0] == 0xA7);
  assert(got.devicePublicKey[0] == 0xC8);
  assert(got.ownerPublicKey[0] == 0x53);
  assert(std::strcmp(got.ownerId, "lab-owner") == 0);

  // Corruption is still fail-closed: never turn a corrupt owner into "no owner".
  Preferences::corrupt(NvsOwnerEnrollmentStore::kNamespace,
                       NvsOwnerEnrollmentStore::kKey);
  assert(!rebooted.load(got));

  // Unavailable NVS remains a hard error: do not silently provision a new key.
  Preferences::failOpen = true;
  assert(!rebooted.load(got));
  assert(!rebooted.save(expected));
  assert(!rebooted.clear());
  Preferences::failOpen = false;

  Preferences::reset();
  assert(store.load(blank));
  assert(!blank.enrolled && !blank.hasDeviceKey);
  std::cout << "PASS: fresh NVS, reboot, corruption and NVS failure\\n";
  return 0;
}
