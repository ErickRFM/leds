#pragma once
#include <cstddef>
#include <cstdint>

#include "SessionCrypto.h"

namespace esp32core {

/**
 * Persisted owner-enrollment state.
 *
 * The device key pair is the long-lived identity of THIS board; the owner
 * public key, owner id and the last accepted session counter must survive a
 * reboot so that replay protection and ownership are stable across resets.
 *
 * On production ESP32 hardware this record is stored in NVS. Flash encryption
 * protects it at rest; the transport must never expose the private key.
 */
struct OwnerEnrollmentSnapshot {
  bool hasDeviceKey = false;
  bool enrolled = false;
  bool revoked = false;
  uint8_t devicePrivateKey[kSessionPrivateKeyBytes] = {};
  uint8_t devicePublicKey[kSessionPublicKeyBytes] = {};
  uint8_t ownerPublicKey[kSessionPublicKeyBytes] = {};
  char ownerId[33] = {};  // matches OwnerEnrollment::kOwnerIdMax
  uint32_t lastCounter = 0;
};

/**
 * Storage seam for OwnerEnrollmentSession. The core stays portable: firmware
 * injects an NVS-backed implementation, host tests an in-memory fake.
 *
 * Every method returns false on failure. Callers must fail closed: a board that
 * cannot load or persist owner state must NOT fall back to allowing commands.
 */
class OwnerEnrollmentStore {
 public:
  virtual ~OwnerEnrollmentStore() = default;
  virtual bool load(OwnerEnrollmentSnapshot& out) = 0;
  virtual bool save(const OwnerEnrollmentSnapshot& snapshot) = 0;
  virtual bool clear() = 0;
};

}  // namespace esp32core
