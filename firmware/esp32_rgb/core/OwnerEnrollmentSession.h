#pragma once
#include <cstddef>
#include <cstdint>
#include <cstring>

#include "CommandAuthorization.h"
#include "OwnerEnrollment.h"
#include "OwnerEnrollmentFrames.h"
#include "OwnerEnrollmentStore.h"
#include "SessionCrypto.h"
#include "SessionKeySchedule.h"

namespace esp32core {

/**
 * Firmware-side owner-enrollment controller.
 *
 * Composes the portable pieces the transport needs into one object:
 *   framing (OwnerEnrollmentFrames) -> enrollment state machine
 *   (OwnerEnrollment) -> crypto (SessionCrypto + SessionKeySchedule) ->
 *   persistence (OwnerEnrollmentStore).
 *
 * It is transport agnostic and allocation free: the BLE/SoftAP layer feeds it
 * one ASCII line at a time from whatever channel already exists (no new UUIDs)
 * and notifies it of connect/disconnect. Host tests drive it with the
 * deterministic SessionCrypto double and an in-memory store, so reboot,
 * replay and recovery behaviour stay automatically verified.
 *
 * Fail-closed rules:
 *  - A board that cannot load or persist its owner state never authenticates.
 *  - A reboot always lands unauthenticated (owner retained, session dropped).
 *  - Recovery/factory reset requires a window opened by a physical gesture.
 */
class OwnerEnrollmentSession final {
 public:
  static constexpr size_t kNonceBytes = SessionKeySchedule::kNonceBytes;
  static constexpr size_t kNonceHexLength = kNonceBytes * 2;
  static constexpr size_t kOwnerIdMax = OwnerEnrollment::kOwnerIdMax;

  OwnerEnrollmentSession(SessionCrypto& crypto, OwnerEnrollmentStore& store)
      : crypto_(crypto), store_(store) {}

  /**
   * Load (or create) the device identity and any persisted owner. Call once at
   * boot. Returns false when storage is unusable; the caller must then keep the
   * privileged transport disabled.
   */
  bool begin() {
    OwnerEnrollmentSnapshot snapshot;
    if (!store_.load(snapshot)) return false;

    if (snapshot.hasDeviceKey) {
      std::memcpy(deviceKeys_.privateKey, snapshot.devicePrivateKey,
                  kSessionPrivateKeyBytes);
      std::memcpy(deviceKeys_.publicKey, snapshot.devicePublicKey,
                  kSessionPublicKeyBytes);
    } else {
      if (!crypto_.generateKeyPair(deviceKeys_)) return false;
      snapshot.hasDeviceKey = true;
      std::memcpy(snapshot.devicePrivateKey, deviceKeys_.privateKey,
                  kSessionPrivateKeyBytes);
      std::memcpy(snapshot.devicePublicKey, deviceKeys_.publicKey,
                  kSessionPublicKeyBytes);
      if (!store_.save(snapshot)) return false;
    }

    if (snapshot.enrolled) {
      char ownerHex[OwnerEnrollment::kPubKeyHexMax + 1];
      toHex(snapshot.ownerPublicKey, kSessionPublicKeyBytes, ownerHex);
      if (enrollment_.restoreOwner(snapshot.ownerId, ownerHex, snapshot.lastCounter,
                                   snapshot.revoked) != EnrollmentResult::OK) {
        return false;
      }
      std::memcpy(ownerPublic_, snapshot.ownerPublicKey, kSessionPublicKeyBytes);
      hasOwnerPublic_ = true;
    }
    return true;
  }

  /** Physical gesture: open a time-boxed enrollment window with a fresh code. */
  bool openPairingWindow(uint32_t nowMs, uint32_t windowMs, const char* oneTimeCode) {
    nowMs_ = nowMs;
    return enrollment_.openPairingWindow(nowMs, windowMs, oneTimeCode) ==
           EnrollmentResult::OK;
  }

  /** Physical gesture: open a time-boxed recovery window with a fresh code. */
  bool openRecoveryWindow(uint32_t nowMs, uint32_t windowMs, const char* oneTimeCode) {
    nowMs_ = nowMs;
    return enrollment_.openRecovery(nowMs, windowMs, oneTimeCode) ==
           EnrollmentResult::OK;
  }

  /** Generate a one-time code from the CSPRNG (8 hex chars). */
  bool generateOneTimeCode(char out[OwnerEnrollment::kOneTimeCodeLength + 1]) {
    uint8_t raw[OwnerEnrollment::kOneTimeCodeLength / 2];
    if (!crypto_.randomBytes(raw, sizeof(raw))) return false;
    toHex(raw, sizeof(raw), out);
    return true;
  }

  void tick(uint32_t nowMs) {
    nowMs_ = nowMs;
    if (enrollment_.state() == EnrollmentState::PAIRING_WINDOW &&
        !enrollment_.windowOpen(nowMs)) {
      enrollment_.tick(nowMs);
    }
  }

  /** Any disconnect drops the session but keeps ownership. */
  void onDisconnect() { enrollment_.onDisconnect(); }

  /**
   * Handle one request line and write the reply (always NUL terminated when
   * capacity > 0). Returns false only when the reply did not fit.
   */
  bool handleFrame(const char* line, char* replyOut, size_t capacity) {
    if (!replyOut || capacity == 0) return false;
    replyOut[0] = '\0';

    EnrollmentFrame frame;
    if (!OwnerEnrollmentFrames::parse(line, frame)) {
      return writeErr(replyOut, capacity, "malformed");
    }

    switch (frame.verb) {
      case EnrollmentVerb::PAIR:
        return handlePair(frame, replyOut, capacity);
      case EnrollmentVerb::BEGIN:
        return handleBegin(replyOut, capacity);
      case EnrollmentVerb::AUTH:
        return handleAuth(frame, replyOut, capacity);
      case EnrollmentVerb::REVOKE:
        // Revoking keeps the owner record (for a later physical recovery) but
        // drops the session; persistence must reflect the revoked state.
        enrollment_.revoke();
        if (!persist()) return writeErr(replyOut, capacity, "persist_failed");
        return writeOk(replyOut, capacity);
      case EnrollmentVerb::RECOVER:
        // Recovery is a physical action; a client frame can never open it.
        return writeErr(replyOut, capacity, "recovery_requires_physical");
      case EnrollmentVerb::RESET:
        return handleReset(frame, replyOut, capacity);
      default:
        // Board-originated verbs must never be accepted as client requests.
        return writeErr(replyOut, capacity, "malformed");
    }
  }

  EnrollmentState state() const { return enrollment_.state(); }
  bool authenticated() const { return enrollment_.authenticated(); }
  bool hasOwner() const { return enrollment_.hasOwner(); }
  const char* ownerId() const { return enrollment_.ownerId(); }
  uint32_t lastCounter() const { return enrollment_.lastAcceptedCounter(); }
  const char* reason() const { return enrollment_.reason(); }

  AuthorizationDecision decide(CommandSource source, PrivilegeClass privilege) const {
    return CommandAuthorization::decide(enrollment_, source, privilege);
  }

 private:
  bool handlePair(const EnrollmentFrame& frame, char* replyOut, size_t capacity) {
    const EnrollmentResult result =
        enrollment_.enroll(frame.ownerId, frame.pubKeyHex, frame.code, nowMs_);
    if (result != EnrollmentResult::OK) {
      return writeErr(replyOut, capacity, enrollment_.reason());
    }
    uint8_t publicKey[kSessionPublicKeyBytes];
    if (!fromHex(frame.pubKeyHex, kPubKeyLength, publicKey)) {
      enrollment_.revoke();
      return writeErr(replyOut, capacity, "invalid_pubkey");
    }
    std::memcpy(ownerPublic_, publicKey, kSessionPublicKeyBytes);
    hasOwnerPublic_ = true;
    if (!persist()) return writeErr(replyOut, capacity, "persist_failed");
    return writeOk(replyOut, capacity);
  }

  bool handleBegin(char* replyOut, size_t capacity) {
    char deviceNonceHex[kNonceHexLength + 1];
    if (!generateNonceHex(deviceNonceHex)) {
      return writeErr(replyOut, capacity, "rng_failed");
    }
    if (enrollment_.beginSession(deviceNonceHex, nowMs_) != SessionChallengeResult::OK) {
      return writeErr(replyOut, capacity, enrollment_.reason());
    }
    std::memcpy(deviceNonceHex_, deviceNonceHex, sizeof(deviceNonceHex_));
    fromHex(deviceNonceHex, kNonceBytes, deviceNonceBin_);
    if (!OwnerEnrollmentFrames::encodeChallenge(replyOut, capacity, deviceNonceHex)) {
      return writeErr(replyOut, capacity, "reply_too_long");
    }
    return true;
  }

  bool handleAuth(const EnrollmentFrame& frame, char* replyOut, size_t capacity) {
    bool proofValid = false;
    if (hasOwnerPublic_) {
      uint8_t clientNonceBin[kNonceBytes];
      if (std::strlen(frame.clientNonce) == kNonceHexLength &&
          fromHex(frame.clientNonce, kNonceBytes, clientNonceBin)) {
        uint8_t presented[kSessionMacBytes];
        uint8_t sessionKey[kSessionKeyBytes];
        uint8_t expected[kSessionMacBytes];
        if (fromHex(frame.proofHex, kSessionMacBytes, presented) &&
            SessionKeySchedule::derive(crypto_, deviceKeys_.privateKey, ownerPublic_,
                                       deviceNonceBin_, clientNonceBin, frame.counter,
                                       sessionKey, expected)) {
          proofValid =
              SessionKeySchedule::constantTimeEquals(expected, presented, kSessionMacBytes);
        }
        secureZero(sessionKey, sizeof(sessionKey));
      }
    }
    const EnrollmentResult result = enrollment_.completeSession(
        deviceNonceHex_, frame.clientNonce, frame.counter, proofValid);
    if (result != EnrollmentResult::OK) {
      return writeErr(replyOut, capacity, enrollment_.reason());
    }
    if (!persist()) return writeErr(replyOut, capacity, "persist_failed");
    return writeOk(replyOut, capacity);
  }

  bool handleReset(const EnrollmentFrame& frame, char* replyOut, size_t capacity) {
    const EnrollmentResult result = enrollment_.factoryReset(frame.code, nowMs_);
    if (result != EnrollmentResult::OK) {
      return writeErr(replyOut, capacity, enrollment_.reason());
    }
    hasOwnerPublic_ = false;
    secureZero(ownerPublic_, sizeof(ownerPublic_));
    if (!persist()) return writeErr(replyOut, capacity, "persist_failed");
    return writeOk(replyOut, capacity);
  }

  /** Persist the current owner state. On failure, fail closed (revoke session). */
  bool persist() {
    OwnerEnrollmentSnapshot snapshot;
    snapshot.hasDeviceKey = true;
    std::memcpy(snapshot.devicePrivateKey, deviceKeys_.privateKey,
                kSessionPrivateKeyBytes);
    std::memcpy(snapshot.devicePublicKey, deviceKeys_.publicKey, kSessionPublicKeyBytes);
    snapshot.enrolled = hasOwnerPublic_ && enrollment_.ownerId()[0] != '\0';
    snapshot.revoked = enrollment_.state() == EnrollmentState::REVOKED;
    if (snapshot.enrolled) {
      std::memcpy(snapshot.ownerPublicKey, ownerPublic_, kSessionPublicKeyBytes);
      std::memcpy(snapshot.ownerId, enrollment_.ownerId(), kOwnerIdMax + 1);
      snapshot.ownerId[kOwnerIdMax] = '\0';
    }
    snapshot.lastCounter = enrollment_.lastAcceptedCounter();
    if (!store_.save(snapshot)) {
      enrollment_.revoke();
      return false;
    }
    return true;
  }

  bool generateNonceHex(char out[kNonceHexLength + 1]) {
    uint8_t nonce[kNonceBytes];
    if (!crypto_.randomBytes(nonce, sizeof(nonce))) return false;
    toHex(nonce, sizeof(nonce), out);
    return true;
  }

  static bool writeOk(char* out, size_t cap) {
    return OwnerEnrollmentFrames::encodeOk(out, cap) != 0;
  }

  static bool writeErr(char* out, size_t cap, const char* reason) {
    return OwnerEnrollmentFrames::encodeErr(out, cap, reason) != 0;
  }

  static void toHex(const uint8_t* in, size_t length, char* out) {
    static const char* digits = "0123456789abcdef";
    for (size_t i = 0; i < length; ++i) {
      out[2 * i] = digits[(in[i] >> 4) & 0x0F];
      out[2 * i + 1] = digits[in[i] & 0x0F];
    }
    out[2 * length] = '\0';
  }

  static bool fromHex(const char* in, size_t length, uint8_t* out) {
    if (!in) return false;
    if (std::strlen(in) != 2 * length) return false;
    for (size_t i = 0; i < length; ++i) {
      int hi = nibble(in[2 * i]);
      int lo = nibble(in[2 * i + 1]);
      if (hi < 0 || lo < 0) return false;
      out[i] = static_cast<uint8_t>((hi << 4) | lo);
    }
    return true;
  }

  static int nibble(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
  }

  static void secureZero(void* buffer, size_t length) {
    volatile uint8_t* p = static_cast<volatile uint8_t*>(buffer);
    while (length-- > 0) *p++ = 0;
  }

  static constexpr size_t kPubKeyLength = kSessionPublicKeyBytes;

  SessionCrypto& crypto_;
  OwnerEnrollmentStore& store_;
  OwnerEnrollment enrollment_;
  SessionKeyPair deviceKeys_{};
  uint8_t ownerPublic_[kSessionPublicKeyBytes] = {};
  bool hasOwnerPublic_ = false;
  char deviceNonceHex_[kNonceHexLength + 1] = {};
  uint8_t deviceNonceBin_[kNonceBytes] = {};
  uint32_t nowMs_ = 0;
};

}  // namespace esp32core
