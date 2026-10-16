#pragma once
#include <cstddef>
#include <cstdint>
#include <cstring>

#include "SessionCrypto.h"

namespace esp32core {

/**
 * Derives the session key and the enrollment proof from the ECDH shared secret.
 *
 * Both the board and the client run this with the same inputs (their own
 * private key, the peer public key and both nonces) and must reach the same
 * outputs; the proof is what the client sends and the board re-derives to
 * authenticate the session. Binding is explicit: the session key binds both
 * nonces, the proof additionally binds the monotonic counter in big-endian form
 * so a replayed (or re-ordered) proof never validates.
 *
 * The schedule is intentionally simple and auditable: HMAC-SHA256 used as a KDF
 * over the ECDH secret. No key material is written anywhere except the outputs.
 */
class SessionKeySchedule final {
 public:
  static constexpr size_t kNonceBytes = 32;
  static constexpr size_t kCounterBytes = 4;

  static bool derive(SessionCrypto& crypto,
                     const uint8_t devicePrivateKey[kSessionPrivateKeyBytes],
                     const uint8_t ownerPublicKey[kSessionPublicKeyBytes],
                     const uint8_t deviceNonce[kSessionNonceBytes],
                     const uint8_t clientNonce[kSessionNonceBytes],
                     uint32_t counter, uint8_t sessionKeyOut[kSessionKeyBytes],
                     uint8_t proofOut[kSessionMacBytes]) {
    if (!sessionKeyOut || !proofOut) return false;

    uint8_t secret[kSessionSharedSecretBytes];
    if (!crypto.sharedSecret(devicePrivateKey, ownerPublicKey, secret)) {
      return false;
    }

    uint8_t buffer[kLabelMax + 2 * kSessionNonceBytes + sizeof(uint32_t)];

    buffer[0] = static_cast<uint8_t>('C');
    std::memcpy(buffer + 1, kSessionLabel, kSessionLabelLength);
    std::memcpy(buffer + 1 + kSessionLabelLength, deviceNonce, kSessionNonceBytes);
    std::memcpy(buffer + 1 + kSessionLabelLength + kSessionNonceBytes, clientNonce,
                kSessionNonceBytes);
    const size_t sessionLength = 1 + kSessionLabelLength + 2 * kSessionNonceBytes;
    if (!crypto.hmacSha256(secret, kSessionSharedSecretBytes, buffer, sessionLength,
                           sessionKeyOut)) {
      secureZero(secret, sizeof(secret));
      return false;
    }

    buffer[0] = static_cast<uint8_t>('P');
    std::memcpy(buffer + 1, kProofLabel, kProofLabelLength);
    std::memcpy(buffer + 1 + kProofLabelLength, deviceNonce, kSessionNonceBytes);
    std::memcpy(buffer + 1 + kProofLabelLength + kSessionNonceBytes, clientNonce,
                kSessionNonceBytes);
    uint8_t* const counterSlot =
        buffer + 1 + kProofLabelLength + 2 * kSessionNonceBytes;
    counterSlot[0] = static_cast<uint8_t>((counter >> 24) & 0xFF);
    counterSlot[1] = static_cast<uint8_t>((counter >> 16) & 0xFF);
    counterSlot[2] = static_cast<uint8_t>((counter >> 8) & 0xFF);
    counterSlot[3] = static_cast<uint8_t>(counter & 0xFF);
    const size_t proofLength =
        1 + kProofLabelLength + 2 * kSessionNonceBytes + sizeof(uint32_t);
    const bool proofOk = crypto.hmacSha256(sessionKeyOut, kSessionKeyBytes, buffer,
                                           proofLength, proofOut);
    secureZero(buffer, sizeof(buffer));
    secureZero(secret, sizeof(secret));
    return proofOk;
  }

  /** Constant-time comparison of two equal-length byte spans. */
  static bool constantTimeEquals(const uint8_t* a, const uint8_t* b, size_t len) {
    if (!a || !b) return false;
    uint8_t diff = 0;
    for (size_t i = 0; i < len; ++i) {
      diff |= static_cast<uint8_t>(a[i] ^ b[i]);
    }
    return diff == 0;
  }

 private:
  static constexpr char kSessionLabel[] = "compi/1 session";
  static constexpr size_t kSessionLabelLength = sizeof(kSessionLabel) - 1;
  static constexpr char kProofLabel[] = "compi/1 proof";
  static constexpr size_t kProofLabelLength = sizeof(kProofLabel) - 1;
  static constexpr size_t kLabelMax = kSessionLabelLength;  // >= proof label

  static void secureZero(void* buffer, size_t len) {
    volatile uint8_t* p = static_cast<volatile uint8_t*>(buffer);
    while (len-- > 0) {
      *p++ = 0;
    }
  }
};

}  // namespace esp32core
