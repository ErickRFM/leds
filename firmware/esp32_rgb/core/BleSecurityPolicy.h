#pragma once
#include <cstddef>
#include <cstdint>
#include <cstring>

#include "CompiCommandArbiter.h"

namespace esp32core {

/**
 * Deterministic BLE link-security and owner-authorization state machine.
 *
 * Security rules enforced here (hardware independent, host-testable):
 *  - A connection always starts unauthenticated; connecting grants nothing.
 *  - Pairing/bonding/encryption NEVER authorize by themselves. They only make
 *    an encrypted link available to present the owner key over.
 *  - The owner key is accepted only on an encrypted link, and only if it
 *    matches the expected key byte for byte.
 *  - Any disconnect revokes authorization unconditionally.
 *  - Only BleAuthState::AUTHORIZED maps to a privileged BLE command source.
 *
 * This class allocates nothing, touches no GPIO/BLE/HTTP and is safe to compile
 * in host unit tests. The firmware wires it to real GAP events; the transport
 * stays fail-closed until the policy reaches AUTHORIZED.
 */
class BleSecurityPolicy final {
 public:
  static constexpr const char* kAuthPrefix = "AUTH ";
  static constexpr size_t kAuthPrefixLength = 5;

  BleAuthState state() const { return state_; }
  const char* reason() const { return reason_; }
  bool encrypted() const { return encrypted_; }
  bool bonded() const { return bonded_; }
  bool authorized() const { return state_ == BleAuthState::AUTHORIZED; }

  /** Command source derived from the current state; fail-closed by default. */
  CommandSource source() const {
    return CompiCommandArbiter::mapBleSource(state_);
  }

  /** A peer connected: unauthenticated until it explicitly authorizes. */
  void onConnected() {
    state_ = BleAuthState::CONNECTED_UNAUTHENTICATED;
    encrypted_ = false;
    bonded_ = false;
    reason_ = "connected_unauthenticated";
  }

  /** Pairing (SMP) has started; still grants no privileges. */
  void onPairingStarted() {
    if (state_ == BleAuthState::DISCONNECTED) return;
    state_ = BleAuthState::PAIRING;
    reason_ = "pairing";
  }

  /** Pairing failed; privileges remain revoked until a clean reconnect. */
  void onPairingFailed() {
    state_ = BleAuthState::FAILED;
    reason_ = "pairing_failed";
  }

  /**
   * Link security (encryption / bonding) completed. Even a fully bonded peer
   * stays unauthenticated: bonding is not ownership. bonded=true only advances
   * the *link* state so the owner key can be presented over an encrypted link.
   */
  void onLinkSecured(bool bonded) {
    if (state_ == BleAuthState::DISCONNECTED) return;
    encrypted_ = true;
    bonded_ = bonded;
    state_ = bonded ? BleAuthState::BONDED : BleAuthState::ENCRYPTED;
    reason_ = bonded ? "bonded" : "encrypted";
  }

  /**
   * Present the owner key. Returns true only when the link is encrypted, the
   * key is non-empty and matches exactly. Wrong keys are rejected without
   * granting anything; a later correct presentation may still succeed.
   */
  bool authorize(const char* presented, const char* expected) {
    if (state_ == BleAuthState::DISCONNECTED) {
      state_ = BleAuthState::FAILED;
      reason_ = "no_active_link";
      return false;
    }
    if (!encrypted_) {
      reason_ = "unencrypted_link";
      return false;
    }
    if (!keyMatches(presented, expected)) {
      reason_ = "invalid_owner_key";
      return false;
    }
    state_ = BleAuthState::AUTHORIZED;
    reason_ = "authorized";
    return true;
  }

  /** Drop privileges while keeping the link; the key may be presented again. */
  void revoke() {
    if (state_ == BleAuthState::DISCONNECTED || state_ == BleAuthState::FAILED) return;
    if (encrypted_) {
      state_ = bonded_ ? BleAuthState::BONDED : BleAuthState::ENCRYPTED;
    } else {
      state_ = BleAuthState::CONNECTED_UNAUTHENTICATED;
    }
    reason_ = "revoked";
  }

  /** Disconnect revokes authorization and forgets the transient link state. */
  void onDisconnected() {
    state_ = BleAuthState::DISCONNECTED;
    encrypted_ = false;
    bonded_ = false;
    reason_ = "disconnected";
  }

  /** "AUTH <key>" is the only owner-authorization frame accepted over GATT. */
  static bool looksLikeAuth(const char* command) {
    if (!command) return false;
    for (size_t i = 0; i < kAuthPrefixLength; ++i) {
      if (command[i] != kAuthPrefix[i]) return false;
    }
    return command[kAuthPrefixLength] != '\0';
  }

  /** Returns the key pointer after "AUTH " or nullptr when not an auth frame. */
  static const char* authKey(const char* command) {
    return looksLikeAuth(command) ? command + kAuthPrefixLength : nullptr;
  }

  /**
   * Constant-ish bounded equality: rejects empty/mismatched lengths and does
   * not short-circuit on the first differing byte.
   */
  static bool keyMatches(const char* presented, const char* expected) {
    if (!presented || !expected) return false;
    const size_t presentedLength = std::strlen(presented);
    const size_t expectedLength = std::strlen(expected);
    if (!presentedLength || presentedLength != expectedLength) return false;
    unsigned diff = 0;
    for (size_t i = 0; i < expectedLength; ++i) {
      diff |= static_cast<unsigned>(static_cast<uint8_t>(presented[i]) ^
                                    static_cast<uint8_t>(expected[i]));
    }
    return diff == 0;
  }

 private:
  BleAuthState state_ = BleAuthState::DISCONNECTED;
  bool encrypted_ = false;
  bool bonded_ = false;
  const char* reason_ = "disconnected";
};

}  // namespace esp32core
