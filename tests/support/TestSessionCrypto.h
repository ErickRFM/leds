#pragma once
#include <cstddef>
#include <cstdint>
#include <cstring>

#include "../../firmware/esp32_rgb/core/SessionCrypto.h"

namespace esp32core_test {

/**
 * Deterministic, NON-cryptographic replacement for the real primitives.
 *
 * It exists only so host tests can exercise SessionKeySchedule's wiring
 * (input binding, counter encoding, failure propagation) without mbedTLS. It is
 * intentionally weak and MUST never be compiled into firmware.
 */
class TestOnlySessionCrypto final : public esp32core::SessionCrypto {
 public:
  bool failSharedSecret = false;
  bool failHmac = false;

  bool randomBytes(uint8_t* out, size_t len) override {
    if (!out) return false;
    for (size_t i = 0; i < len; ++i) {
      out[i] = static_cast<uint8_t>((state_ = state_ * 1664525u + 1013904223u) >> 24);
    }
    return true;
  }

  bool generateKeyPair(esp32core::SessionKeyPair& out) override {
    if (!randomBytes(out.privateKey, sizeof(out.privateKey))) return false;
    publicOf(out.privateKey, out.publicKey);
    return true;
  }

  bool sharedSecret(const uint8_t privateKey[esp32core::kSessionPrivateKeyBytes],
                    const uint8_t peerPublicKey[esp32core::kSessionPublicKeyBytes],
                    uint8_t secretOut[esp32core::kSessionSharedSecretBytes]) override {
    if (failSharedSecret) return false;
    for (size_t i = 0; i < esp32core::kSessionSharedSecretBytes; ++i) {
      secretOut[i] = static_cast<uint8_t>(privateKey[i] ^ peerPublicKey[i]);
    }
    return true;
  }

  bool hmacSha256(const uint8_t* key, size_t keyLen, const uint8_t* data, size_t dataLen,
                  uint8_t macOut[esp32core::kSessionMacBytes]) override {
    if (failHmac) return false;
    uint64_t hash = 1469598103934665603ull;
    auto fold = [&hash](const uint8_t* bytes, size_t len) {
      for (size_t i = 0; i < len; ++i) {
        hash ^= bytes[i];
        hash *= 1099511628211ull;
      }
    };
    fold(key, keyLen);
    fold(data, dataLen);
    for (size_t i = 0; i < esp32core::kSessionMacBytes; ++i) {
      hash ^= hash >> 29;
      hash *= 0x9e3779b97f4a7c15ull;
      macOut[i] = static_cast<uint8_t>(hash >> 56);
    }
    return true;
  }

  bool aeadSeal(const uint8_t*, const uint8_t*, const uint8_t*, size_t, const uint8_t* plaintext,
                size_t plaintextLen, uint8_t* ciphertextOut,
                uint8_t tagOut[esp32core::kSessionTagBytes]) override {
    for (size_t i = 0; i < plaintextLen; ++i) ciphertextOut[i] = static_cast<uint8_t>(plaintext[i] ^ 0x5a);
    std::memset(tagOut, 0xA5, esp32core::kSessionTagBytes);
    return true;
  }

  bool aeadOpen(const uint8_t*, const uint8_t*, const uint8_t*, size_t, const uint8_t* ciphertext,
                size_t ciphertextLen, const uint8_t tag[esp32core::kSessionTagBytes],
                uint8_t* plaintextOut) override {
    for (size_t i = 0; i < esp32core::kSessionTagBytes; ++i) {
      if (tag[i] != 0xA5) return false;
    }
    for (size_t i = 0; i < ciphertextLen; ++i) plaintextOut[i] = static_cast<uint8_t>(ciphertext[i] ^ 0x5a);
    return true;
  }

  /** Mirrors the fake's shared-secret symmetry: pub = priv ^ 0x5a. */
  static void publicOf(const uint8_t privateKey[esp32core::kSessionPrivateKeyBytes],
                       uint8_t publicOut[esp32core::kSessionPublicKeyBytes]) {
    for (size_t i = 0; i < esp32core::kSessionPublicKeyBytes; ++i) {
      publicOut[i] = static_cast<uint8_t>(privateKey[i] ^ 0x5a);
    }
  }

 private:
  uint32_t state_ = 0x12345678u;
};

}  // namespace esp32core_test
