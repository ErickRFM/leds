// SEG.1b-2b — firmware-side owner-enrollment controller.
//
// Drives OwnerEnrollmentSession exactly as the BLE transport will: frames in,
// frames out, with the deterministic SessionCrypto double and an in-memory
// store. Covers first boot, enrollment, session auth, REBOOT persistence and
// replay protection across restart, revoke/recovery, and fail-closed storage.

#include <cassert>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <string>

#include "../firmware/esp32_rgb/core/OwnerEnrollmentSession.h"
#include "../firmware/esp32_rgb/core/OwnerEnrollmentStore.h"
#include "support/TestSessionCrypto.h"

using esp32core::EnrollmentState;
using esp32core::OwnerEnrollmentSession;
using esp32core::OwnerEnrollmentSnapshot;
using esp32core::OwnerEnrollmentStore;
using esp32core::SessionKeyPair;
using esp32core::SessionKeySchedule;
using esp32core_test::TestOnlySessionCrypto;

namespace {

constexpr size_t kNonceBytes = SessionKeySchedule::kNonceBytes;
constexpr size_t kNonceHexLength = kNonceBytes * 2;
constexpr size_t kPubKeyBytes = 32;
const char* kCode = "deadbeef";
const char* kClientNonceHex = "00112233445566778899aabbccddeeff";

struct MemoryStore final : public OwnerEnrollmentStore {
  OwnerEnrollmentSnapshot snapshot;
  bool failLoad = false;
  bool failSave = false;

  bool load(OwnerEnrollmentSnapshot& out) override {
    if (failLoad) return false;
    out = snapshot;
    return true;
  }
  bool save(const OwnerEnrollmentSnapshot& in) override {
    if (failSave) return false;
    snapshot = in;
    return true;
  }
  bool clear() override {
    snapshot = OwnerEnrollmentSnapshot{};
    return true;
  }
};

void toHex(const uint8_t* in, size_t n, char* out) {
  static const char* digits = "0123456789abcdef";
  for (size_t i = 0; i < n; ++i) {
    out[2 * i] = digits[(in[i] >> 4) & 0xF];
    out[2 * i + 1] = digits[in[i] & 0xF];
  }
  out[2 * n] = '\0';
}

void fromHex(const char* in, uint8_t* out, size_t n) {
  auto nibble = [](char c) -> int {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return 0;
  };
  for (size_t i = 0; i < n; ++i) {
    out[i] = static_cast<uint8_t>((nibble(in[2 * i]) << 4) | nibble(in[2 * i + 1]));
  }
}

std::string challengeNonce(const std::string& reply) {
  assert(reply.rfind("CHALLENGE ", 0) == 0);
  return reply.substr(10);
}

std::string authLine(TestOnlySessionCrypto& crypto, const SessionKeyPair& owner,
                     const uint8_t devicePublic[kPubKeyBytes], const char* deviceNonceHex,
                     const char* clientNonceHex, uint32_t counter) {
  uint8_t deviceNonceBin[kNonceBytes];
  uint8_t clientNonceBin[kNonceBytes];
  fromHex(deviceNonceHex, deviceNonceBin, kNonceBytes);
  fromHex(clientNonceHex, clientNonceBin, kNonceBytes);
  uint8_t sessionKey[32];
  uint8_t proof[32];
  assert(SessionKeySchedule::derive(crypto, owner.privateKey, devicePublic, deviceNonceBin,
                                    clientNonceBin, counter, sessionKey, proof));
  char proofHex[65];
  toHex(proof, sizeof(proof), proofHex);
  return std::string("AUTH ") + clientNonceHex + " " + std::to_string(counter) + " " +
         proofHex;
}

// Full client handshake: BEGIN then AUTH with a valid proof. Returns the AUTH
// reply (the CHALLENGE reply is consumed internally).
std::string attemptAuth(OwnerEnrollmentSession& session, TestOnlySessionCrypto& crypto,
                        const SessionKeyPair& owner, const uint8_t* devicePublic,
                        uint32_t counter) {
  char reply[192];
  assert(session.handleFrame("BEGIN 00112233445566778899aabbccddeeff", reply, sizeof(reply)));
  const std::string deviceNonce = challengeNonce(reply);
  assert(deviceNonce.size() == kNonceHexLength);
  assert(session.handleFrame(authLine(crypto, owner, devicePublic, deviceNonce.c_str(),
                                      kClientNonceHex, counter)
                                 .c_str(),
                             reply, sizeof(reply)));
  return reply;
}

std::string pairLine(const char* ownerPubKeyHex) {
  return std::string("PAIR ") + kCode + " owner-1 " + ownerPubKeyHex;
}

}  // namespace

int main() {
  MemoryStore store;
  TestOnlySessionCrypto crypto;
  SessionKeyPair owner;
  assert(crypto.generateKeyPair(owner));
  char ownerPubHex[kPubKeyBytes * 2 + 1];
  toHex(owner.publicKey, kPubKeyBytes, ownerPubHex);

  char reply[192];
  std::string response;

  // ---- First boot generates and persists the device identity --------------
  {
    OwnerEnrollmentSession session(crypto, store);
    assert(session.begin());
    assert(!session.hasOwner());
    assert(store.snapshot.hasDeviceKey);
    assert(!store.snapshot.enrolled);
  }

  uint8_t devicePublic[kPubKeyBytes];
  std::memcpy(devicePublic, store.snapshot.devicePublicKey, kPubKeyBytes);

  // ---- A reboot reuses the same device key and stays unpaired -------------
  {
    OwnerEnrollmentSession rebooted(crypto, store);
    assert(rebooted.begin());
    assert(rebooted.state() == EnrollmentState::UNPAIRED);
    assert(std::memcmp(store.snapshot.devicePublicKey, devicePublic, kPubKeyBytes) == 0);
    assert(rebooted.handleFrame(pairLine(ownerPubHex).c_str(), reply, sizeof(reply)));
    assert(std::string(reply).rfind("ERR ", 0) == 0);  // no physical window
  }

  // ---- Physical window + enrollment --------------------------------------
  OwnerEnrollmentSession session(crypto, store);
  assert(session.begin());
  assert(!session.authenticated());
  assert(session.openPairingWindow(1000, 120000, kCode));
  assert(session.handleFrame(pairLine(ownerPubHex).c_str(), reply, sizeof(reply)));
  assert(std::string(reply) == "OK");
  assert(session.hasOwner());
  assert(!session.authenticated());
  assert(store.snapshot.enrolled);
  assert(std::strcmp(store.snapshot.ownerId, "owner-1") == 0);
  assert(std::memcmp(store.snapshot.ownerPublicKey, owner.publicKey, kPubKeyBytes) == 0);

  // Enrolling twice with the owner present is refused (no silent overwrite).
  assert(!session.openPairingWindow(1000, 120000, kCode));
  assert(session.state() == EnrollmentState::ENROLLED);

  // ---- Correct proof authenticates; counter is persisted ------------------
  response = attemptAuth(session, crypto, owner, devicePublic, 1);
  assert(response == "OK");
  assert(session.authenticated());
  assert(store.snapshot.lastCounter == 1);

  // Proving read-only is free; privileged needs the live session.
  assert(session.decide(esp32core::CommandSource::BLE_AUTHENTICATED,
                        esp32core::PrivilegeClass::HARDWARE_WRITE)
             .allowed);

  // ---- Reboot: owner survives, session does not, replay is blocked --------
  OwnerEnrollmentSession afterReboot(crypto, store);
  assert(afterReboot.begin());
  assert(afterReboot.hasOwner());
  assert(!afterReboot.authenticated());
  assert(afterReboot.state() == EnrollmentState::ENROLLED);
  assert(afterReboot.lastCounter() == 1);
  assert(!afterReboot.decide(esp32core::CommandSource::BLE_AUTHENTICATED,
                             esp32core::PrivilegeClass::HARDWARE_WRITE)
              .allowed);

  // Replaying the pre-reboot counter is rejected.
  response = attemptAuth(afterReboot, crypto, owner, devicePublic, 1);
  assert(response.rfind("ERR ", 0) == 0);
  assert(!afterReboot.authenticated());
  // A strictly greater counter works and is persisted.
  response = attemptAuth(afterReboot, crypto, owner, devicePublic, 2);
  assert(response == "OK");
  assert(afterReboot.authenticated());
  assert(store.snapshot.lastCounter == 2);

  // ---- A wrong proof never authenticates ----------------------------------
  afterReboot.onDisconnect();
  assert(afterReboot.handleFrame("BEGIN 00112233445566778899aabbccddeeff", reply,
                                 sizeof(reply)));
  std::string nonce = challengeNonce(reply);
  std::string forged = "AUTH " + std::string(kClientNonceHex) + " 3 " + std::string(64, 'f');
  assert(afterReboot.handleFrame(forged.c_str(), reply, sizeof(reply)));
  assert(std::string(reply).rfind("ERR ", 0) == 0);
  assert(!afterReboot.authenticated());

  // ---- Revoke persists and survives a reboot ------------------------------
  assert(afterReboot.handleFrame("REVOKE", reply, sizeof(reply)));
  assert(std::string(reply) == "OK");
  assert(store.snapshot.revoked);
  {
    OwnerEnrollmentSession revoked(crypto, store);
    assert(revoked.begin());
    assert(revoked.state() == EnrollmentState::REVOKED);
    assert(!revoked.hasOwner());
    assert(!revoked.decide(esp32core::CommandSource::BLE_AUTHENTICATED,
                           esp32core::PrivilegeClass::ADMIN)
                .allowed);

    // Factory reset needs a physical recovery window and the matching code.
    assert(revoked.handleFrame("RECOVER deadbeef", reply, sizeof(reply)));
    assert(std::string(reply).rfind("ERR ", 0) == 0);  // client cannot open recovery
    assert(revoked.handleFrame("RESET deadbeef", reply, sizeof(reply)));
    assert(std::string(reply).rfind("ERR ", 0) == 0);  // no recovery window yet
    assert(revoked.openRecoveryWindow(1000, 120000, kCode));
    assert(revoked.handleFrame("RESET 00000000", reply, sizeof(reply)));
    assert(std::string(reply).rfind("ERR ", 0) == 0);  // wrong code
    assert(revoked.handleFrame(("RESET " + std::string(kCode)).c_str(), reply,
                               sizeof(reply)));
    assert(std::string(reply) == "OK");
    assert(revoked.state() == EnrollmentState::UNPAIRED);
    assert(!store.snapshot.enrolled);
    assert(!store.snapshot.revoked);
    // The device identity is preserved across a factory reset.
    assert(store.snapshot.hasDeviceKey);
    assert(std::memcmp(store.snapshot.devicePublicKey, devicePublic, kPubKeyBytes) == 0);
  }

  // ---- Fail-closed: storage that cannot persist never enrolls -------------
  {
    MemoryStore failing;
    OwnerEnrollmentSession session2(crypto, failing);
    assert(session2.begin());
    assert(session2.openPairingWindow(1000, 120000, kCode));
    failing.failSave = true;
    assert(session2.handleFrame(pairLine(ownerPubHex).c_str(), reply, sizeof(reply)));
    assert(std::string(reply).find("persist_failed") != std::string::npos);
    assert(!failing.snapshot.enrolled);
    assert(!session2.hasOwner());
  }

  // ---- Fail-closed: storage that cannot load keeps the board locked down ---
  {
    MemoryStore unreadable;
    unreadable.failLoad = true;
    OwnerEnrollmentSession session3(crypto, unreadable);
    assert(!session3.begin());
  }

  (void)nonce;
  return 0;
}
