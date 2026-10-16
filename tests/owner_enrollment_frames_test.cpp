#include <cassert>
#include <string>

#include "../firmware/esp32_rgb/core/OwnerEnrollmentFrames.h"

using esp32core::EnrollmentFrame;
using esp32core::EnrollmentVerb;
using esp32core::OwnerEnrollmentFrames;

static const char* kPub =
    "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
static const char* kProof =
    "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210";

int main() {
  EnrollmentFrame frame;

  // PAIR <code> <ownerId> <pubKeyHex>
  assert(OwnerEnrollmentFrames::parse("PAIR deadbeef owner-1 $PUB", frame) == false);
  {
    std::string line = std::string("PAIR deadbeef owner-1 ") + kPub;
    assert(OwnerEnrollmentFrames::parse(line.c_str(), frame));
  }
  assert(frame.verb == EnrollmentVerb::PAIR);
  assert(std::string(frame.code) == "deadbeef");
  assert(std::string(frame.ownerId) == "owner-1");
  assert(std::string(frame.pubKeyHex) == kPub);

  // Arity and field-length are strict.
  assert(!OwnerEnrollmentFrames::parse("PAIR deadbeef owner-1", frame));
  assert(!OwnerEnrollmentFrames::parse("PAIR deadbeef owner-1 pub extra", frame));
  assert(!OwnerEnrollmentFrames::parse("PAIR toolongcode owner-1", frame));  // > 8 code? no
  assert(!OwnerEnrollmentFrames::parse("PAIR deadbeef owner-1 abcd", frame));
  // Empty tokens / trailing spaces are rejected.
  assert(!OwnerEnrollmentFrames::parse("PAIR  deadbeef x", frame));
  assert(!OwnerEnrollmentFrames::parse("OK ", frame));

  // BEGIN <clientNonce>
  assert(OwnerEnrollmentFrames::parse("BEGIN 00112233", frame));
  assert(frame.verb == EnrollmentVerb::BEGIN);
  assert(std::string(frame.clientNonce) == "00112233");
  assert(!OwnerEnrollmentFrames::parse("BEGIN", frame));
  assert(!OwnerEnrollmentFrames::parse("BEGIN a b", frame));

  // AUTH <clientNonce> <counter> <proofHex>
  std::string auth = std::string("AUTH ffeeddcc 42 ") + kProof;
  assert(OwnerEnrollmentFrames::parse(auth.c_str(), frame));
  assert(frame.verb == EnrollmentVerb::AUTH);
  assert(std::string(frame.clientNonce) == "ffeeddcc");
  assert(frame.counter == 42);
  assert(std::string(frame.proofHex) == kProof);
  assert(!OwnerEnrollmentFrames::parse("AUTH ffeeddcc 42", frame));
  const std::string authBadCounter = std::string("AUTH ffeeddcc abc ") + kProof;
  assert(!OwnerEnrollmentFrames::parse(authBadCounter.c_str(), frame));
  const std::string authNegative = std::string("AUTH ffeeddcc -1 ") + kProof;
  assert(!OwnerEnrollmentFrames::parse(authNegative.c_str(), frame));
  // Counter overflow is rejected.
  const std::string authOverflow = std::string("AUTH ffeeddcc 99999999999 ") + kProof;
  assert(!OwnerEnrollmentFrames::parse(authOverflow.c_str(), frame));

  // REVOKE / RECOVER / RESET
  assert(OwnerEnrollmentFrames::parse("REVOKE", frame));
  assert(frame.verb == EnrollmentVerb::REVOKE);
  assert(!OwnerEnrollmentFrames::parse("REVOKE now", frame));
  assert(OwnerEnrollmentFrames::parse("RECOVER deadbeef", frame));
  assert(frame.verb == EnrollmentVerb::RECOVER);
  assert(std::string(frame.code) == "deadbeef");
  assert(OwnerEnrollmentFrames::parse("RESET deadbeef", frame));
  assert(frame.verb == EnrollmentVerb::RESET);

  // Board verbs are recognised for round-trip.
  assert(OwnerEnrollmentFrames::parse("CHALLENGE 00112233445566778899aabbccddeeff", frame));
  assert(frame.verb == EnrollmentVerb::CHALLENGE);
  assert(OwnerEnrollmentFrames::parse("OK", frame));
  assert(frame.verb == EnrollmentVerb::OK);
  assert(OwnerEnrollmentFrames::parse("ERR bad_code", frame));
  assert(frame.verb == EnrollmentVerb::ERR);
  assert(std::string(frame.reason) == "bad_code");

  // Unknown / malformed lines are rejected.
  assert(!OwnerEnrollmentFrames::parse("", frame));
  assert(!OwnerEnrollmentFrames::parse(nullptr, frame));
  assert(!OwnerEnrollmentFrames::parse("NOPE", frame));
  assert(!OwnerEnrollmentFrames::parse("PAIR", frame));
  assert(!OwnerEnrollmentFrames::parse("   ", frame));

  // Encoders write bounded, canonical responses.
  char challengeBuffer[32];
  const size_t challengeLen =
      OwnerEnrollmentFrames::encodeChallenge(challengeBuffer, sizeof(challengeBuffer), "00112233");
  assert(challengeLen == std::string("CHALLENGE 00112233").size());
  assert(std::string(challengeBuffer) == "CHALLENGE 00112233");
  assert(OwnerEnrollmentFrames::encodeChallenge(nullptr, 0, "x") == 0);
  assert(OwnerEnrollmentFrames::encodeErr(nullptr, 0, "x") == 0);

  char okBuffer[8];
  assert(OwnerEnrollmentFrames::encodeOk(okBuffer, sizeof(okBuffer)) == 2);
  assert(std::string(okBuffer) == "OK");

  char small[4];
  assert(OwnerEnrollmentFrames::encodeErr(small, sizeof(small), "bad_code") == 0);
  assert(small[0] == '\0');

  char errBuffer[32];
  const size_t errLen = OwnerEnrollmentFrames::encodeErr(errBuffer, sizeof(errBuffer), "replay");
  assert(errLen == std::string("ERR replay").size());
  assert(std::string(errBuffer) == "ERR replay");

  return 0;
}
