// SEG.3 — adversarial security QA over the full stack:
//   framing (OwnerEnrollmentFrames) -> enrollment (OwnerEnrollment)
//   -> crypto (SessionKeySchedule + SessionCrypto) -> authorization.
//
// This is an integration host test: it composes the real pure components the
// way the GATT wiring will, then attacks the composition. No hardware is
// touched; all primitives run through the deterministic test double.

#include <cassert>
#include <cstdint>
#include <initializer_list>
#include <string>

#include "../firmware/esp32_rgb/core/CommandAuthorization.h"
#include "../firmware/esp32_rgb/core/OwnerEnrollment.h"
#include "../firmware/esp32_rgb/core/OwnerEnrollmentFrames.h"
#include "../firmware/esp32_rgb/core/SessionKeySchedule.h"
#include "support/TestSessionCrypto.h"

using esp32core::CommandAuthorization;
using esp32core::CommandSource;
using esp32core::EnrollmentFrame;
using esp32core::EnrollmentResult;
using esp32core::EnrollmentState;
using esp32core::EnrollmentVerb;
using esp32core::kSessionKeyBytes;
using esp32core::kSessionMacBytes;
using esp32core::OwnerEnrollment;
using esp32core::OwnerEnrollmentFrames;
using esp32core::PrivilegeClass;
using esp32core::SessionChallengeResult;
using esp32core::SessionKeyPair;
using esp32core::SessionKeySchedule;
using esp32core_test::TestOnlySessionCrypto;

static constexpr size_t kNonceBytes = SessionKeySchedule::kNonceBytes;

static const char* kCode = "deadbeef";

static void toHex(const uint8_t* in, size_t n, char* out) {
  static const char* digits = "0123456789abcdef";
  for (size_t i = 0; i < n; ++i) {
    out[2 * i] = digits[(in[i] >> 4) & 0xF];
    out[2 * i + 1] = digits[in[i] & 0xF];
  }
  out[2 * n] = '\0';
}

static bool fromHex(const char* in, uint8_t* out, size_t n) {
  if (!in || std::string(in).size() != 2 * n) return false;
  auto nibble = [](char c) -> int {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
  };
  for (size_t i = 0; i < n; ++i) {
    const int hi = nibble(in[2 * i]);
    const int lo = nibble(in[2 * i + 1]);
    if (hi < 0 || lo < 0) return false;
    out[i] = static_cast<uint8_t>((hi << 4) | lo);
  }
  return true;
}

// 16-byte nonce hex (32 chars) zero-padded into the 32-byte schedule nonce.
static void padNonce(const char* hex, uint8_t out[kNonceBytes]) {
  uint8_t raw[16];
  assert(fromHex(hex, raw, sizeof(raw)));
  for (size_t i = 0; i < kNonceBytes; ++i) out[i] = 0;
  for (size_t i = 0; i < sizeof(raw); ++i) out[i] = raw[i];
}

/** Board-side harness: the exact composition the transport will wire. */
struct DeviceHarness {
  OwnerEnrollment enrollment;
  TestOnlySessionCrypto crypto;
  SessionKeyPair deviceKeys;
  uint8_t ownerPublic[32] = {};
  bool hasOwnerPublic = false;
  char pendingDeviceNonceHex[33] = {};
  uint8_t pendingDeviceNonceBin[kNonceBytes] = {};
  uint32_t now = 1000;

  DeviceHarness() { assert(crypto.generateKeyPair(deviceKeys)); }

  std::string handle(const char* line) {
    EnrollmentFrame frame;
    if (!OwnerEnrollmentFrames::parse(line, frame)) return "ERR malformed";
    char response[96];
    switch (frame.verb) {
      case EnrollmentVerb::PAIR: {
        const EnrollmentResult result =
            enrollment.enroll(frame.ownerId, frame.pubKeyHex, frame.code, now);
        if (result != EnrollmentResult::OK) {
          OwnerEnrollmentFrames::encodeErr(response, sizeof(response), enrollment.reason());
          return response;
        }
        hasOwnerPublic = fromHex(frame.pubKeyHex, ownerPublic, sizeof(ownerPublic));
        return OwnerEnrollmentFrames::encodeOk(response, sizeof(response)) ? "OK" : "ERR err";
      }
      case EnrollmentVerb::BEGIN: {
        uint8_t fresh[16];
        assert(crypto.randomBytes(fresh, sizeof(fresh)));
        toHex(fresh, sizeof(fresh), pendingDeviceNonceHex);
        const SessionChallengeResult result = enrollment.beginSession(pendingDeviceNonceHex, now);
        if (result != SessionChallengeResult::OK) {
          OwnerEnrollmentFrames::encodeErr(response, sizeof(response), enrollment.reason());
          return response;
        }
        padNonce(pendingDeviceNonceHex, pendingDeviceNonceBin);
        OwnerEnrollmentFrames::encodeChallenge(response, sizeof(response), pendingDeviceNonceHex);
        return response;
      }
      case EnrollmentVerb::AUTH: {
        uint8_t clientNonceBin[kNonceBytes];
        padNonce(frame.clientNonce, clientNonceBin);
        bool proofValid = false;
        if (hasOwnerPublic) {
          uint8_t sessionKey[kSessionKeyBytes];
          uint8_t expected[kSessionMacBytes];
          assert(SessionKeySchedule::derive(crypto, deviceKeys.privateKey, ownerPublic,
                                           pendingDeviceNonceBin, clientNonceBin, frame.counter,
                                           sessionKey, expected));
          uint8_t presented[kSessionMacBytes];
          if (fromHex(frame.proofHex, presented, sizeof(presented))) {
            proofValid =
                SessionKeySchedule::constantTimeEquals(expected, presented, kSessionMacBytes);
          }
        }
        const EnrollmentResult result = enrollment.completeSession(
            pendingDeviceNonceHex, frame.clientNonce, frame.counter, proofValid);
        if (result != EnrollmentResult::OK) {
          OwnerEnrollmentFrames::encodeErr(response, sizeof(response), enrollment.reason());
          return response;
        }
        return OwnerEnrollmentFrames::encodeOk(response, sizeof(response)) ? "OK" : "ERR err";
      }
      case EnrollmentVerb::REVOKE:
        enrollment.revoke();
        return OwnerEnrollmentFrames::encodeOk(response, sizeof(response)) ? "OK" : "ERR err";
      case EnrollmentVerb::RECOVER:
        if (enrollment.openRecovery(now, 120000, frame.code) != EnrollmentResult::OK) {
          OwnerEnrollmentFrames::encodeErr(response, sizeof(response), enrollment.reason());
          return response;
        }
        return OwnerEnrollmentFrames::encodeOk(response, sizeof(response)) ? "OK" : "ERR err";
      case EnrollmentVerb::RESET:
        if (enrollment.factoryReset(frame.code, now) != EnrollmentResult::OK) {
          OwnerEnrollmentFrames::encodeErr(response, sizeof(response), enrollment.reason());
          return response;
        }
        return OwnerEnrollmentFrames::encodeOk(response, sizeof(response)) ? "OK" : "ERR err";
      default:
        // Board-originated verbs (CHALLENGE/OK/ERR) must never be accepted as
        // client requests.
        return "ERR malformed";
    }
  }

  bool privilegedAllowed(PrivilegeClass privilege) const {
    return CommandAuthorization::decide(enrollment, CommandSource::BLE_AUTHENTICATED, privilege)
        .allowed;
  }

  uint32_t lastCounter() const { return enrollment.lastAcceptedCounter(); }
};

static std::string challengeNonce(const std::string& response) {
  assert(response.rfind("CHALLENGE ", 0) == 0);
  return response.substr(10);
}

static std::string clientAuthLine(TestOnlySessionCrypto& crypto, const SessionKeyPair& ownerKeys,
                                  const SessionKeyPair& deviceKeys, const char* clientNonceHex,
                                  const char* deviceNonceHex, uint32_t counter) {
  uint8_t deviceNonceBin[kNonceBytes];
  uint8_t clientNonceBin[kNonceBytes];
  padNonce(deviceNonceHex, deviceNonceBin);
  padNonce(clientNonceHex, clientNonceBin);
  uint8_t sessionKey[kSessionKeyBytes];
  uint8_t proof[kSessionMacBytes];
  assert(SessionKeySchedule::derive(crypto, ownerKeys.privateKey, deviceKeys.publicKey,
                                    deviceNonceBin, clientNonceBin, counter, sessionKey, proof));
  char proofHex[65];
  toHex(proof, sizeof(proof), proofHex);
  return std::string("AUTH ") + clientNonceHex + " " + std::to_string(counter) + " " + proofHex;
}

int main() {
  DeviceHarness device;
  TestOnlySessionCrypto crypto;
  SessionKeyPair ownerKeys;
  assert(crypto.generateKeyPair(ownerKeys));
  char ownerPubHex[65];
  toHex(ownerKeys.publicKey, sizeof(ownerKeys.publicKey), ownerPubHex);
  const std::string pair = std::string("PAIR ") + kCode + " owner-1 " + ownerPubHex;
  const char* clientNonceHex = "ffeeddccbbaa99887766554433221100";

  // ---- Unenrolled device: nothing privileged, PAIR outside window refused ---
  assert(!device.privilegedAllowed(PrivilegeClass::HARDWARE_WRITE));
  assert(!device.privilegedAllowed(PrivilegeClass::ADMIN));
  assert(device.handle(pair.c_str()).rfind("ERR ", 0) == 0);

  // ---- Malformed / injected frames are rejected ---------------------------
  assert(device.handle("") == "ERR malformed");
  assert(device.handle("GARBAGE") == "ERR malformed");
  assert(device.handle("PAIR") == "ERR malformed");
  assert(device.handle("AUTH only-two") == "ERR malformed");
  // A client must not be able to inject board-originated verbs as requests.
  assert(device.handle("OK") == "ERR malformed");
  assert(device.handle("CHALLENGE 00112233445566778899aabbccddeeff") == "ERR malformed");

  // ---- Physical window + one-time code ------------------------------------
  assert(device.enrollment.openPairingWindow(device.now, 120000, kCode) == EnrollmentResult::OK);
  // Wrong one-time code inside the window is refused.
  assert(device.handle((std::string("PAIR 00000000 owner-1 ") + ownerPubHex).c_str())
             .rfind("ERR ", 0) == 0);
  assert(device.handle(pair.c_str()) == "OK");
  assert(device.enrollment.state() == EnrollmentState::ENROLLED);
  assert(device.enrollment.hasOwner());
  // A second window cannot be opened while enrolled.
  assert(device.enrollment.openPairingWindow(device.now, 120000, kCode) ==
         EnrollmentResult::ALREADY_ENROLLED);
  // Enrolled but unauthenticated: still no privilege.
  assert(!device.privilegedAllowed(PrivilegeClass::HARDWARE_WRITE));

  // ---- BEGIN issues a challenge; correct proof authenticates ---------------
  std::string deviceNonceHex = challengeNonce(device.handle("BEGIN 00112233445566778899aabbccddeeff"));
  assert(device.handle(clientAuthLine(crypto, ownerKeys, device.deviceKeys, clientNonceHex,
                                      deviceNonceHex.c_str(), 1)
                           .c_str()) == "OK");
  assert(device.enrollment.state() == EnrollmentState::AUTHENTICATED);
  assert(device.privilegedAllowed(PrivilegeClass::HARDWARE_WRITE));
  assert(device.privilegedAllowed(PrivilegeClass::ADMIN));
  assert(device.lastCounter() == 1);

  // ---- Reused AUTH without a fresh BEGIN has no pending session ------------
  std::string forgedSame = clientAuthLine(crypto, ownerKeys, device.deviceKeys, clientNonceHex,
                                          deviceNonceHex.c_str(), 2);
  assert(device.handle(forgedSame.c_str()).rfind("ERR ", 0) == 0);

  // ---- Wrong proof is rejected; privilege is never granted ---------------
  device.enrollment.onDisconnect();
  deviceNonceHex = challengeNonce(device.handle("BEGIN 00112233445566778899aabbccddeeff"));
  assert(device.handle((std::string("AUTH ") + clientNonceHex + " 2 " + std::string(64, 'f'))
                           .c_str())
             .rfind("ERR ", 0) == 0);
  assert(!device.privilegedAllowed(PrivilegeClass::HARDWARE_WRITE));

  // ---- Replay: a counter that does not strictly increase is refused --------
  assert(device.handle(clientAuthLine(crypto, ownerKeys, device.deviceKeys, clientNonceHex,
                                      deviceNonceHex.c_str(), 2)
                           .c_str()) == "OK");
  device.enrollment.onDisconnect();
  deviceNonceHex = challengeNonce(device.handle("BEGIN 00112233445566778899aabbccddeeff"));
  // Re-using counter 2 (the last accepted) is a replay.
  assert(device.handle(clientAuthLine(crypto, ownerKeys, device.deviceKeys, clientNonceHex,
                                      deviceNonceHex.c_str(), 2)
                           .c_str())
             .rfind("ERR ", 0) == 0);
  assert(!device.privilegedAllowed(PrivilegeClass::HARDWARE_WRITE));
  // A strictly greater counter works.
  assert(device.handle(clientAuthLine(crypto, ownerKeys, device.deviceKeys, clientNonceHex,
                                      deviceNonceHex.c_str(), 3)
                           .c_str()) == "OK");

  // ---- Concurrency / cancellation: a newer BEGIN cancels the pending one ---
  const std::string oldNonce = challengeNonce(device.handle("BEGIN 11111111111111111111111111111111"));
  const std::string newNonce = challengeNonce(device.handle("BEGIN 22222222222222222222222222222222"));
  assert(oldNonce != newNonce);
  device.enrollment.onDisconnect();
  // Re-issue a challenge so a session is pending, then answer the stale one.
  const std::string liveNonce = challengeNonce(device.handle("BEGIN 33333333333333333333333333333333"));
  assert(device.handle(clientAuthLine(crypto, ownerKeys, device.deviceKeys, clientNonceHex,
                                      oldNonce.c_str(), 4)
                           .c_str())
             .rfind("ERR ", 0) == 0);
  assert(device.handle(clientAuthLine(crypto, ownerKeys, device.deviceKeys, clientNonceHex,
                                      liveNonce.c_str(), 4)
                           .c_str()) == "OK");

  // ---- BLE -> Wi-Fi transition: losing the BLE session keeps the owner -----
  assert(device.enrollment.hasOwner());
  assert(device.privilegedAllowed(PrivilegeClass::HARDWARE_WRITE));
  device.enrollment.onDisconnect();
  assert(device.enrollment.hasOwner());
  assert(!device.enrollment.authenticated());
  assert(!device.privilegedAllowed(PrivilegeClass::HARDWARE_WRITE));
  // The local SoftAP owner channel is unaffected by the BLE drop.
  assert(CommandAuthorization::decide(device.enrollment, CommandSource::WIFI_SOFTAP_LOCAL,
                                      PrivilegeClass::FIRMWARE_UPDATE)
             .allowed);
  // ...but internal automations still cannot change the network.
  assert(!CommandAuthorization::decide(device.enrollment, CommandSource::AUTOMATION_INTERNAL,
                                       PrivilegeClass::NETWORK_CHANGE)
              .allowed);

  // ---- Denial of GPIO / scripts / credentials without authorization -------
  for (PrivilegeClass privilege :
       {PrivilegeClass::HARDWARE_WRITE, PrivilegeClass::SCRIPT_AUTOMATION,
        PrivilegeClass::NETWORK_CHANGE, PrivilegeClass::PROJECT_INSTALL,
        PrivilegeClass::FIRMWARE_UPDATE, PrivilegeClass::ADMIN}) {
    assert(!device.privilegedAllowed(privilege));
    // SoftAP local remains the owner channel.
    assert(CommandAuthorization::decide(device.enrollment, CommandSource::WIFI_SOFTAP_LOCAL,
                                        privilege)
               .allowed);
  }

  // ---- Session expiration: an expired physical window cannot enroll --------
  DeviceHarness expiring;
  assert(expiring.enrollment.openPairingWindow(100, 50, kCode) == EnrollmentResult::OK);
  expiring.now = 200;  // past the deadline
  char expiredPair[96];
  std::string pairLine = std::string("PAIR ") + kCode + " owner-1 " + ownerPubHex;
  assert(expiring.handle(pairLine.c_str()).rfind("ERR ", 0) == 0);
  assert(expiring.enrollment.state() == EnrollmentState::UNPAIRED);
  assert(!expiring.enrollment.hasOwner());
  (void)expiredPair;

  // ---- Owner revoke: session and future enrollment stop until recovery -----
  assert(device.handle("REVOKE") == "OK");
  assert(device.enrollment.state() == EnrollmentState::REVOKED);
  assert(!device.privilegedAllowed(PrivilegeClass::HARDWARE_WRITE));
  // BEGIN and AUTH are both refused while revoked.
  assert(device.handle("BEGIN 00112233445566778899aabbccddeeff").rfind("ERR ", 0) == 0);
  assert(device.handle(clientAuthLine(crypto, ownerKeys, device.deviceKeys, clientNonceHex,
                                      "00112233445566778899aabbccddeeff", 5)
                           .c_str())
             .rfind("ERR ", 0) == 0);
  // Direct re-enrollment is refused without recovery.
  assert(device.handle(pair.c_str()).rfind("ERR ", 0) == 0);

  // ---- Owner loss & recovery: malformed code fails, right code wipes owner -
  // Recovery is opened by a physical window that issues a fresh one-time code;
  // a malformed code is refused, and factory reset must echo that code.
  assert(device.handle("RECOVER nothex!!").rfind("ERR ", 0) == 0);
  assert(device.handle(("RECOVER " + std::string(kCode)).c_str()) == "OK");
  assert(device.handle("RESET 00000000").rfind("ERR ", 0) == 0);
  assert(device.handle((std::string("RESET ") + kCode).c_str()) == "OK");
  assert(device.enrollment.state() == EnrollmentState::UNPAIRED);
  assert(!device.enrollment.hasOwner());
  assert(!device.privilegedAllowed(PrivilegeClass::ADMIN));

  // ---- Recovery re-enrollment works again from scratch --------------------
  assert(device.enrollment.openPairingWindow(device.now, 120000, kCode) == EnrollmentResult::OK);
  assert(device.handle(pair.c_str()) == "OK");
  deviceNonceHex = challengeNonce(device.handle("BEGIN 00112233445566778899aabbccddeeff"));
  // Counter restarts after a factory reset.
  assert(device.handle(clientAuthLine(crypto, ownerKeys, device.deviceKeys, clientNonceHex,
                                      deviceNonceHex.c_str(), 1)
                           .c_str()) == "OK");
  assert(device.privilegedAllowed(PrivilegeClass::HARDWARE_WRITE));

  return 0;
}

