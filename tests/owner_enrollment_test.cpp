#include <cassert>
#include <string>

#include "../firmware/esp32_rgb/core/OwnerEnrollment.h"

using esp32core::EnrollmentResult;
using esp32core::EnrollmentState;
using esp32core::OwnerEnrollment;
using esp32core::SessionChallengeResult;

// 64-hex X25519 public key (32 bytes) and 32-hex 16-byte nonces.
static const char* kPubA =
    "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
static const char* kPubB =
    "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210";
static const char* kDevNonce = "00112233445566778899aabbccddeeff";
static const char* kClientNonce = "ffeeddccbbaa99887766554433221100";
static const char* kCode = "deadbeef";

int main() {
  // Fresh device is unpaired and strictly fail-closed.
  OwnerEnrollment enrollment;
  assert(enrollment.state() == EnrollmentState::UNPAIRED);
  assert(!enrollment.hasOwner());
  assert(!enrollment.authenticated());
  assert(!OwnerEnrollment::commandAllowed(enrollment.state()));

  // Enrolling before any physical window is refused.
  assert(enrollment.enroll("owner-1", kPubA, kCode, 1000) ==
         EnrollmentResult::NOT_IN_WINDOW);

  // Physical window requires a valid one-time code and non-zero duration.
  assert(enrollment.openPairingWindow(1000, 0, kCode) ==
         EnrollmentResult::INVALID_ARGUMENT);
  assert(enrollment.openPairingWindow(1000, 120000, "nothex!!") ==
         EnrollmentResult::INVALID_ARGUMENT);
  assert(enrollment.openPairingWindow(1000, 120000, kCode) == EnrollmentResult::OK);
  assert(enrollment.windowOpen(1000));
  assert(enrollment.windowOpen(120999));
  assert(!enrollment.windowOpen(121000));

  // Wrong code inside the window is refused and grants nothing.
  assert(enrollment.enroll("owner-1", kPubA, "00000000", 1100) ==
         EnrollmentResult::BAD_CODE);
  assert(enrollment.state() == EnrollmentState::PAIRING_WINDOW);
  assert(!enrollment.hasOwner());

  // Malformed owner id / public key are refused.
  assert(enrollment.enroll("bad id!", kPubA, kCode, 1100) ==
         EnrollmentResult::INVALID_ARGUMENT);
  assert(enrollment.enroll("owner-1", "abcd", kCode, 1100) ==
         EnrollmentResult::INVALID_ARGUMENT);

  // Hex digits encode bytes: Android displays uppercase, device generates
  // lowercase. The same valid physical code must work in either case.
  assert(enrollment.enroll("owner-1", kPubA, "DEADBEE0", 1100) ==
         EnrollmentResult::BAD_CODE);
  assert(enrollment.enroll("owner-1", kPubA, "ZZZZZZZZ", 1100) ==
         EnrollmentResult::BAD_CODE);
  // Correct enrollment lands in ENROLLED, not yet authenticated.
  assert(enrollment.enroll("owner-1", kPubA, "DeAdBeEf", 1100) == EnrollmentResult::OK);
  assert(enrollment.state() == EnrollmentState::ENROLLED);
  assert(enrollment.hasOwner());
  assert(!enrollment.authenticated());
  assert(!OwnerEnrollment::commandAllowed(enrollment.state()));
  assert(std::string(enrollment.ownerId()) == "owner-1");

  // A second window cannot be opened while an owner exists.
  assert(enrollment.openPairingWindow(1200, 120000, kCode) ==
         EnrollmentResult::ALREADY_ENROLLED);

  // Session completion without a challenge is refused.
  assert(enrollment.completeSession(kDevNonce, kClientNonce, 1, true) ==
         EnrollmentResult::NO_SESSION);

  // Begin session requires a well-formed device nonce.
  assert(enrollment.beginSession("zz", 1200) == SessionChallengeResult::NOT_ENROLLED);
  assert(enrollment.beginSession(kDevNonce, 1200) == SessionChallengeResult::OK);

  // A wrong challenge echo is rejected.
  assert(enrollment.completeSession("00", kClientNonce, 1, true) ==
         EnrollmentResult::BAD_PROOF);
  assert(!enrollment.authenticated());

  // Bad proof is rejected.
  assert(enrollment.completeSession(kDevNonce, kClientNonce, 1, false) ==
         EnrollmentResult::BAD_PROOF);
  assert(!enrollment.authenticated());

  // Valid proof authenticates the session.
  assert(enrollment.completeSession(kDevNonce, kClientNonce, 1, true) ==
         EnrollmentResult::OK);
  assert(enrollment.state() == EnrollmentState::AUTHENTICATED);
  assert(enrollment.authenticated());
  assert(OwnerEnrollment::commandAllowed(enrollment.state()));
  assert(enrollment.lastAcceptedCounter() == 1);

  // Disconnect drops authorization but keeps the owner enrolled.
  enrollment.onDisconnect();
  assert(enrollment.state() == EnrollmentState::ENROLLED);
  assert(!enrollment.authenticated());

  // Replay: a counter that does not strictly increase is refused.
  assert(enrollment.beginSession(kDevNonce, 1300) == SessionChallengeResult::OK);
  assert(enrollment.completeSession(kDevNonce, kClientNonce, 1, true) ==
         EnrollmentResult::REPLAY);
  assert(!enrollment.authenticated());

  // A strictly greater counter authenticates.
  assert(enrollment.completeSession(kDevNonce, kClientNonce, 2, true) ==
         EnrollmentResult::OK);
  assert(enrollment.authenticated());

  // Revoked sessions never work again and cannot start a new session.
  enrollment.revoke();
  assert(enrollment.state() == EnrollmentState::REVOKED);
  assert(!enrollment.authenticated());
  assert(enrollment.beginSession(kDevNonce, 1400) == SessionChallengeResult::REVOKED);
  assert(enrollment.completeSession(kDevNonce, kClientNonce, 3, true) ==
         EnrollmentResult::REVOKED);
  // Re-enrolling in REVOKED state (without recovery) is refused.
  assert(enrollment.enroll("owner-2", kPubB, kCode, 1400) ==
         EnrollmentResult::NOT_IN_WINDOW);

  // Recovery uses a physical window + one-time code.
  assert(enrollment.openRecovery(1400, 120000, kCode) == EnrollmentResult::OK);
  assert(enrollment.state() == EnrollmentState::RECOVERY);
  assert(!enrollment.authenticated());

  // Factory reset requires the recovery code and wipes the owner.
  assert(enrollment.factoryReset("00000000", 1500) == EnrollmentResult::BAD_CODE);
  assert(enrollment.state() == EnrollmentState::RECOVERY);
  assert(enrollment.factoryReset("DEADBEEF", 1500) == EnrollmentResult::OK);
  assert(enrollment.state() == EnrollmentState::UNPAIRED);
  assert(!enrollment.hasOwner());
  assert(!enrollment.authenticated());

  // A fresh device can now be enrolled by a new owner and authenticated.
  OwnerEnrollment reEnrolled;
  assert(reEnrolled.openPairingWindow(2000, 60000, "1234abcd") == EnrollmentResult::OK);
  assert(reEnrolled.enroll("owner-2", kPubB, "1234abcd", 2100) == EnrollmentResult::OK);
  assert(reEnrolled.beginSession(kDevNonce, 2100) == SessionChallengeResult::OK);
  assert(reEnrolled.completeSession(kDevNonce, kClientNonce, 1, true) ==
         EnrollmentResult::OK);
  assert(reEnrolled.authenticated());

  // Window expiry closes the window and returns to the base state.
  OwnerEnrollment expired;
  assert(expired.openPairingWindow(100, 50, kCode) == EnrollmentResult::OK);
  expired.tick(200);
  assert(expired.state() == EnrollmentState::UNPAIRED);
  assert(!expired.windowOpen(200));
  assert(expired.enroll("owner-1", kPubA, kCode, 200) ==
         EnrollmentResult::NOT_IN_WINDOW);

  return 0;
}
