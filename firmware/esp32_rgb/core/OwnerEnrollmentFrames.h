#pragma once
#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <cstring>

namespace esp32core {

/**
 * Canonical verbs on the owner-enrollment channel. Every frame is a single
 * ASCII line of space-separated tokens; unknown verbs or wrong arity are
 * rejected (fail-closed). Framing only: no crypto, no allocation.
 *
 *   client -> board   PAIR <code> <ownerId> <pubKeyHex>
 *   client -> board   BEGIN <clientNonce>
 *   board  -> client  CHALLENGE <deviceNonce>
 *   client -> board   AUTH <clientNonce> <counter> <proofHex>
 *   board  -> client  OK
 *   board  -> client  ERR <reason>
 *   client -> board   REVOKE
 *   client -> board   RECOVER <code>
 *   client -> board   RESET <code>
 */
enum class EnrollmentVerb : uint8_t {
  NONE = 0,
  PAIR,
  BEGIN,
  AUTH,
  REVOKE,
  RECOVER,
  RESET,
  CHALLENGE,
  OK,
  ERR
};

struct EnrollmentFrame {
  EnrollmentVerb verb = EnrollmentVerb::NONE;
  char code[9] = {};        // 8 hex + null
  char ownerId[33] = {};    // up to 32 + null
  char pubKeyHex[65] = {};  // 64 hex + null
  char clientNonce[33] = {};// up to 32 hex + null
  char deviceNonce[33] = {};// up to 32 hex + null
  uint32_t counter = 0;
  char proofHex[65] = {};   // up to 64 hex + null
  char reason[33] = {};     // for ERR, up to 32 + null
};

class OwnerEnrollmentFrames final {
 public:
  static constexpr size_t kMaxTokens = 5;
  static constexpr size_t kTokenMax = 65;  // longest field (pubkey/proof) + null
  static constexpr size_t kMaxLine = 256;

  /** Strict parse of a single request line. Returns false on any malformed input. */
  static bool parse(const char* line, EnrollmentFrame& out) {
    out = EnrollmentFrame{};
    if (!line) return false;
    const size_t length = std::strlen(line);
    if (length == 0 || length >= kMaxLine) return false;

    char tokens[kMaxTokens][kTokenMax];
    size_t count = 0;
    if (!tokenize(line, tokens, count)) return false;
    if (count == 0) return false;

    if (equals(tokens[0], "PAIR")) {
      if (count != 4) return false;
      out.verb = EnrollmentVerb::PAIR;
      if (!copyBounded(out.code, tokens[1], 8)) return false;
      if (!copyBounded(out.ownerId, tokens[2], 32)) return false;
      if (!copyBounded(out.pubKeyHex, tokens[3], 64)) return false;
      return true;
    }
    if (equals(tokens[0], "BEGIN")) {
      if (count != 2) return false;
      out.verb = EnrollmentVerb::BEGIN;
      if (!copyBounded(out.clientNonce, tokens[1], 32)) return false;
      return true;
    }
    if (equals(tokens[0], "AUTH")) {
      if (count != 4) return false;
      out.verb = EnrollmentVerb::AUTH;
      if (!copyBounded(out.clientNonce, tokens[1], 32)) return false;
      if (!parseCounter(tokens[2], out.counter)) return false;
      if (!copyBounded(out.proofHex, tokens[3], 64)) return false;
      return true;
    }
    if (equals(tokens[0], "REVOKE")) {
      if (count != 1) return false;
      out.verb = EnrollmentVerb::REVOKE;
      return true;
    }
    if (equals(tokens[0], "RECOVER")) {
      if (count != 2) return false;
      out.verb = EnrollmentVerb::RECOVER;
      if (!copyBounded(out.code, tokens[1], 8)) return false;
      return true;
    }
    if (equals(tokens[0], "RESET")) {
      if (count != 2) return false;
      out.verb = EnrollmentVerb::RESET;
      if (!copyBounded(out.code, tokens[1], 8)) return false;
      return true;
    }
    // Board-originated verbs are recognised so the parser round-trips, but a
    // client must never be able to inject them as requests.
    if (equals(tokens[0], "CHALLENGE")) {
      if (count != 2) return false;
      out.verb = EnrollmentVerb::CHALLENGE;
      return copyBounded(out.deviceNonce, tokens[1], 32);
    }
    if (equals(tokens[0], "OK")) {
      if (count != 1) return false;
      out.verb = EnrollmentVerb::OK;
      return true;
    }
    if (equals(tokens[0], "ERR")) {
      if (count != 2) return false;
      out.verb = EnrollmentVerb::ERR;
      return copyBounded(out.reason, tokens[1], 32);
    }
    return false;
  }

  static size_t encodeChallenge(char* out, size_t cap, const char* deviceNonce) {
    return format(out, cap, "CHALLENGE %s", deviceNonce ? deviceNonce : "");
  }

  static size_t encodeOk(char* out, size_t cap) {
    return format(out, cap, "%s", "OK");
  }

  static size_t encodeErr(char* out, size_t cap, const char* reason) {
    return format(out, cap, "ERR %s", reason ? reason : "error");
  }

 private:
  static bool equals(const char* a, const char* b) {
    return a && b && std::strcmp(a, b) == 0;
  }

  static bool copyBounded(char* destination, const char* source, size_t max) {
    const size_t length = std::strlen(source);
    if (length == 0 || length > max) return false;
    std::memcpy(destination, source, length + 1);
    return true;
  }

  static bool parseCounter(const char* text, uint32_t& out) {
    if (!text || text[0] == '\0') return false;
    uint32_t value = 0;
    for (size_t i = 0; text[i] != '\0'; ++i) {
      const char c = text[i];
      if (c < '0' || c > '9') return false;
      const uint32_t digit = static_cast<uint32_t>(c - '0');
      if (value > (0xFFFFFFFFu - digit) / 10u) return false;  // overflow guard
      value = value * 10u + digit;
    }
    out = value;
    return true;
  }

  /** Split on single spaces. Empty tokens and over-long tokens fail. */
  static bool tokenize(const char* line, char tokens[kMaxTokens][kTokenMax],
                       size_t& count) {
    count = 0;
    size_t index = 0;
    const size_t length = std::strlen(line);
    while (index < length) {
      if (count >= kMaxTokens) return false;
      const size_t start = index;
      while (index < length && line[index] != ' ') ++index;
      const size_t tokenLength = index - start;
      if (tokenLength == 0 || tokenLength >= kTokenMax) return false;
      std::memcpy(tokens[count], line + start, tokenLength);
      tokens[count][tokenLength] = '\0';
      ++count;
      if (index < length) {
        ++index;  // consume the single space
        if (index >= length) return false;  // trailing space => empty token
      }
    }
    return count > 0;
  }

  static size_t format(char* out, size_t cap, const char* pattern, const char* arg) {
    if (!out || cap == 0) return 0;
    const int written = std::snprintf(out, cap, pattern, arg);
    if (written < 0 || static_cast<size_t>(written) >= cap) {
      if (cap > 0) out[0] = '\0';
      return 0;
    }
    return static_cast<size_t>(written);
  }
};

}  // namespace esp32core
