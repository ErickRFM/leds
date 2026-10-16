#pragma once
#include <cstddef>
#include <cstdint>
#include <cstring>

namespace esp32core {

/**
 * Owner enrollment + session authorization state machine (SEG.1).
 *
 * This is the PURE, hardware-independent core of the per-device owner
 * enrollment protocol. It allocates nothing, performs no I/O and performs NO
 * cryptography itself: the actual X25519 ECDH / AES-GCM / HMAC work lives behind
 * a thin firmware adapter that feeds this machine *verified* booleans. That
 * keeps the security *logic* (state transitions, monotonic counters, replay and
 * window checks) fully host-testable and deterministic.
 *
 * Threat model enforced here:
 *  - Connecting or bonding grants NOTHING.
 *  - Enrollment is possible only inside a time-boxed window opened by a
 *    physical gesture, and only with the one-time code issued for that window.
 *  - Commands are authorized per SESSION only after a proof tied to the device
 *    challenge and a strictly increasing counter (anti-replay).
 *  - A revoked session never works again; re-enrollment requires recovery.
 *  - Every unknown/incomplete path is fail-closed (never authorized).
 */
enum class EnrollmentState : uint8_t {
  UNPAIRED = 0,       // No owner stored; privileged paths are off.
  PAIRING_WINDOW = 1, // Physically opened; one-time code valid until nowMs.
  ENROLLED = 2,       // Owner stored; a session must still be authorized.
  AUTHENTICATED = 3,  // Session authorized; privileged commands allowed.
  REVOKED = 4,        // Session/owner revoked; only recovery can re-enroll.
  RECOVERY = 5        // Physical recovery window; factory reset/re-enroll only.
};

enum class EnrollmentResult : uint8_t {
  OK = 0,
  NOT_IN_WINDOW = 1,
  WINDOW_EXPIRED = 2,
  BAD_CODE = 3,
  ALREADY_ENROLLED = 4,
  NOT_ENROLLED = 5,
  NO_SESSION = 6,
  BAD_PROOF = 7,
  REPLAY = 8,
  INVALID_ARGUMENT = 9,
  REVOKED = 10
};

enum class SessionChallengeResult : uint8_t {
  OK = 0,
  NOT_ENROLLED = 1,
  REVOKED = 2
};

class OwnerEnrollment final {
 public:
  static constexpr size_t kOwnerIdMax = 32;
  static constexpr size_t kPubKeyHexMax = 64;   // 32-byte X25519 key as hex
  static constexpr size_t kNonceHexMax = 32;    // 16-byte nonce as hex
  static constexpr size_t kOneTimeCodeLength = 8;
  static constexpr uint32_t kDefaultWindowMs = 120000u;  // 2 minutes

  EnrollmentState state() const { return state_; }
  const char* reason() const { return reason_; }
  bool hasOwner() const { return state_ == EnrollmentState::ENROLLED ||
                                 state_ == EnrollmentState::AUTHENTICATED; }
  /** Only an explicitly authorized session may run privileged commands. */
  bool authenticated() const { return state_ == EnrollmentState::AUTHENTICATED; }
  uint32_t lastAcceptedCounter() const { return lastCounter_; }
  const char* ownerId() const { return ownerId_; }

  /**
   * Physical trigger opened a time-boxed pairing window with a one-time code.
   * Allowed only from UNPAIRED or REVOKED (re-enrollment/recovery), never while
   * an owner session is already established.
   */
  EnrollmentResult openPairingWindow(uint32_t nowMs, uint32_t windowMs,
                                     const char* oneTimeCode) {
    if (state_ == EnrollmentState::ENROLLED || state_ == EnrollmentState::AUTHENTICATED) {
      reason_ = "already_enrolled";
      return EnrollmentResult::ALREADY_ENROLLED;
    }
    if (!validOneTimeCode(oneTimeCode) || windowMs == 0) {
      reason_ = "invalid_window";
      return EnrollmentResult::INVALID_ARGUMENT;
    }
    std::memcpy(oneTimeCode_, oneTimeCode, kOneTimeCodeLength);
    oneTimeCode_[kOneTimeCodeLength] = '\0';
    windowOpenedMs_ = nowMs;
    windowDeadlineMs_ = nowMs + windowMs;
    hasOwnerOnEntry_ = false;
    baseStateOnEntry_ = (state_ == EnrollmentState::REVOKED) ? EnrollmentState::REVOKED
                                                             : EnrollmentState::UNPAIRED;
    state_ = EnrollmentState::PAIRING_WINDOW;
    reason_ = "pairing_window";
    return EnrollmentResult::OK;
  }

  bool windowOpen(uint32_t nowMs) const {
    return state_ == EnrollmentState::PAIRING_WINDOW && nowMs < windowDeadlineMs_;
  }

  /** Close an expired window, returning to the pre-window base state. */
  void tick(uint32_t nowMs) {
    if (state_ != EnrollmentState::PAIRING_WINDOW) return;
    if (nowMs < windowDeadlineMs_) return;
    clearOneTimeCode();
    // A window opened from REVOKED returns to REVOKED; otherwise UNPAIRED,
    // unless an owner already exists (it cannot: window is blocked then).
    state_ = hasOwnerOnEntry_ ? EnrollmentState::ENROLLED : baseStateOnEntry_;
    reason_ = "window_expired";
  }

  /**
   * Enroll the owner. Requires an open window, the matching one-time code and a
   * well-formed owner id/public key. Stores the owner and moves to ENROLLED;
   * authorization still requires a separate session proof.
   */
  EnrollmentResult enroll(const char* ownerId, const char* ownerPubKeyHex,
                          const char* presentedCode, uint32_t nowMs) {
    if (state_ != EnrollmentState::PAIRING_WINDOW) {
      reason_ = "not_in_window";
      return EnrollmentResult::NOT_IN_WINDOW;
    }
    if (nowMs >= windowDeadlineMs_) {
      tick(nowMs);
      reason_ = "window_expired";
      return EnrollmentResult::WINDOW_EXPIRED;
    }
    if (!validOwnerId(ownerId) || !validPubKeyHex(ownerPubKeyHex)) {
      reason_ = "invalid_arguments";
      return EnrollmentResult::INVALID_ARGUMENT;
    }
    if (!constantTimeEquals(presentedCode, oneTimeCode_)) {
      reason_ = "bad_code";
      return EnrollmentResult::BAD_CODE;
    }
    std::memcpy(ownerId_, ownerId, std::strlen(ownerId) + 1);
    std::memcpy(ownerPubKey_, ownerPubKeyHex, std::strlen(ownerPubKeyHex) + 1);
    clearOneTimeCode();
    lastCounter_ = 0;
    hasSession_ = false;
    state_ = EnrollmentState::ENROLLED;
    reason_ = "enrolled";
    return EnrollmentResult::OK;
  }

  /**
   * Device issues a challenge nonce for a new session. Requires an enrolled
   * owner; a revoked owner must first pass recovery.
   */
  SessionChallengeResult beginSession(const char* deviceNonceHex,
                                      uint32_t /*nowMs*/) {
    if (state_ == EnrollmentState::REVOKED) {
      reason_ = "revoked";
      return SessionChallengeResult::REVOKED;
    }
    if (!hasOwner()) {
      reason_ = "not_enrolled";
      return SessionChallengeResult::NOT_ENROLLED;
    }
    if (!validNonceHex(deviceNonceHex)) {
      reason_ = "invalid_nonce";
      return SessionChallengeResult::NOT_ENROLLED;
    }
    std::memcpy(deviceNonce_, deviceNonceHex, std::strlen(deviceNonceHex) + 1);
    hasSession_ = false;
    state_ = EnrollmentState::ENROLLED;
    reason_ = "challenge_issued";
    return SessionChallengeResult::OK;
  }

  /**
   * Complete a session. The caller (crypto layer) must have already verified
   * the proof over the device challenge, the client nonce and the counter.
   * This method enforces structure and anti-replay:
   *  - a challenge must be pending;
   *  - the device nonce must match the one issued;
   *  - the client nonce must be well-formed;
   *  - the counter must strictly increase.
   */
  EnrollmentResult completeSession(const char* deviceNonceHex,
                                   const char* clientNonceHex, uint32_t counter,
                                   bool proofValid) {
    if (state_ == EnrollmentState::REVOKED) {
      reason_ = "revoked";
      return EnrollmentResult::REVOKED;
    }
    if (!hasOwner()) {
      reason_ = "not_enrolled";
      return EnrollmentResult::NOT_ENROLLED;
    }
    if (deviceNonce_[0] == '\0') {
      reason_ = "no_session";
      return EnrollmentResult::NO_SESSION;
    }
    if (!constantTimeEquals(deviceNonceHex, deviceNonce_)) {
      reason_ = "bad_challenge";
      return EnrollmentResult::BAD_PROOF;
    }
    if (!validNonceHex(clientNonceHex)) {
      reason_ = "invalid_nonce";
      return EnrollmentResult::INVALID_ARGUMENT;
    }
    if (counter <= lastCounter_) {
      reason_ = "replay";
      return EnrollmentResult::REPLAY;
    }
    if (!proofValid) {
      reason_ = "bad_proof";
      return EnrollmentResult::BAD_PROOF;
    }
    lastCounter_ = counter;
    hasSession_ = true;
    deviceNonce_[0] = '\0';
    state_ = EnrollmentState::AUTHENTICATED;
    reason_ = "authenticated";
    return EnrollmentResult::OK;
  }

  /** Any disconnect revokes the session; the owner stays enrolled. */
  void onDisconnect() {
    hasSession_ = false;
    deviceNonce_[0] = '\0';
    if (state_ == EnrollmentState::AUTHENTICATED) state_ = EnrollmentState::ENROLLED;
    if (state_ == EnrollmentState::ENROLLED) reason_ = "disconnected";
  }

  /** Revoke owner authorization. Only recovery can re-enroll afterwards. */
  void revoke() {
    hasSession_ = false;
    deviceNonce_[0] = '\0';
    state_ = EnrollmentState::REVOKED;
    reason_ = "revoked";
  }

  /** Physically entered recovery mode (e.g. physical gesture). */
  EnrollmentResult enterRecovery() {
    if (state_ != EnrollmentState::REVOKED &&
        state_ != EnrollmentState::ENROLLED &&
        state_ != EnrollmentState::UNPAIRED) {
      reason_ = "recovery_not_allowed";
      return EnrollmentResult::NOT_IN_WINDOW;
    }
    state_ = EnrollmentState::RECOVERY;
    reason_ = "recovery";
    return EnrollmentResult::OK;
  }

  /** Factory reset clears the owner and returns to UNPAIRED. */
  EnrollmentResult factoryReset(const char* presentedCode, uint32_t nowMs) {
    if (state_ != EnrollmentState::RECOVERY) {
      reason_ = "not_in_recovery";
      return EnrollmentResult::NOT_IN_WINDOW;
    }
    if (windowDeadlineMs_ != 0 && nowMs >= windowDeadlineMs_) {
      clearOneTimeCode();
      state_ = EnrollmentState::REVOKED;
      reason_ = "window_expired";
      return EnrollmentResult::WINDOW_EXPIRED;
    }
    // Recovery still requires the one-time code that was issued with the
    // physical window, so a stale physical action cannot wipe the owner.
    if (oneTimeCode_[0] != '\0' && !constantTimeEquals(presentedCode, oneTimeCode_)) {
      reason_ = "bad_code";
      return EnrollmentResult::BAD_CODE;
    }
    wipeOwner();
    state_ = EnrollmentState::UNPAIRED;
    reason_ = "factory_reset";
    return EnrollmentResult::OK;
  }

  /** Open recovery using the same physical-window + one-time-code mechanism. */
  EnrollmentResult openRecovery(uint32_t nowMs, uint32_t windowMs,
                                const char* oneTimeCode) {
    if (!validOneTimeCode(oneTimeCode) || windowMs == 0) {
      reason_ = "invalid_window";
      return EnrollmentResult::INVALID_ARGUMENT;
    }
    std::memcpy(oneTimeCode_, oneTimeCode, kOneTimeCodeLength);
    oneTimeCode_[kOneTimeCodeLength] = '\0';
    windowOpenedMs_ = nowMs;
    windowDeadlineMs_ = nowMs + windowMs;
    hasOwnerOnEntry_ = hasOwner();
    baseStateOnEntry_ = EnrollmentState::REVOKED;
    state_ = EnrollmentState::RECOVERY;
    reason_ = "recovery";
    return EnrollmentResult::OK;
  }

  /** Static predicate used by the transport to keep unauthenticated traffic out. */
  static bool commandAllowed(EnrollmentState state) {
    return state == EnrollmentState::AUTHENTICATED;
  }

 private:
  static bool validOneTimeCode(const char* code) {
    if (!code) return false;
    for (size_t i = 0; i < kOneTimeCodeLength; ++i) {
      const char c = code[i];
      const bool hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') ||
                       (c >= 'A' && c <= 'F');
      if (!hex) return false;
    }
    return code[kOneTimeCodeLength] == '\0';
  }

  static bool validOwnerId(const char* ownerId) {
    if (!ownerId) return false;
    const size_t length = std::strlen(ownerId);
    if (length == 0 || length > kOwnerIdMax) return false;
    for (size_t i = 0; i < length; ++i) {
      const char c = ownerId[i];
      const bool allowed = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') ||
                           (c >= 'A' && c <= 'Z') || c == '-' || c == '_';
      if (!allowed) return false;
    }
    return true;
  }

  static bool validPubKeyHex(const char* hex) {
    if (!hex) return false;
    const size_t length = std::strlen(hex);
    if (length != kPubKeyHexMax) return false;
    for (size_t i = 0; i < length; ++i) {
      const char c = hex[i];
      if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
        return false;
      }
    }
    return true;
  }

  static bool validNonceHex(const char* hex) {
    if (!hex) return false;
    const size_t length = std::strlen(hex);
    if (length == 0 || length > kNonceHexMax) return false;
    for (size_t i = 0; i < length; ++i) {
      const char c = hex[i];
      if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
        return false;
      }
    }
    return true;
  }

  static bool constantTimeEquals(const char* a, const char* b) {
    if (!a || !b) return false;
    const size_t la = std::strlen(a);
    const size_t lb = std::strlen(b);
    if (la != lb || la == 0) return false;
    unsigned diff = 0;
    for (size_t i = 0; i < la; ++i) {
      diff |= static_cast<unsigned>(static_cast<uint8_t>(a[i]) ^ static_cast<uint8_t>(b[i]));
    }
    return diff == 0;
  }

  void clearOneTimeCode() {
    for (size_t i = 0; i <= kOneTimeCodeLength; ++i) oneTimeCode_[i] = '\0';
  }

  void wipeOwner() {
    for (size_t i = 0; i <= kOwnerIdMax; ++i) ownerId_[i] = '\0';
    for (size_t i = 0; i <= kPubKeyHexMax; ++i) ownerPubKey_[i] = '\0';
    clearOneTimeCode();
    deviceNonce_[0] = '\0';
    lastCounter_ = 0;
    hasSession_ = false;
  }

  EnrollmentState state_ = EnrollmentState::UNPAIRED;
  EnrollmentState baseStateOnEntry_ = EnrollmentState::UNPAIRED;
  bool hasOwnerOnEntry_ = false;
  bool hasSession_ = false;
  uint32_t lastCounter_ = 0;
  uint32_t windowOpenedMs_ = 0;
  uint32_t windowDeadlineMs_ = 0;
  char oneTimeCode_[kOneTimeCodeLength + 1] = {};
  char ownerId_[kOwnerIdMax + 1] = {};
  char ownerPubKey_[kPubKeyHexMax + 1] = {};
  char deviceNonce_[kNonceHexMax + 1] = {};
  const char* reason_ = "unpaired";
};

}  // namespace esp32core
