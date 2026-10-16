#pragma once
#include <cstddef>
#include <cstdint>

namespace esp32core {

/** X25519 / AES-256-GCM / HMAC-SHA256 sized constants. */
constexpr size_t kSessionPublicKeyBytes = 32;
constexpr size_t kSessionPrivateKeyBytes = 32;
constexpr size_t kSessionSharedSecretBytes = 32;
constexpr size_t kSessionKeyBytes = 32;
constexpr size_t kSessionNonceBytes = 12;
constexpr size_t kSessionTagBytes = 16;
constexpr size_t kSessionMacBytes = 32;

struct SessionKeyPair {
  uint8_t publicKey[kSessionPublicKeyBytes];
  uint8_t privateKey[kSessionPrivateKeyBytes];
};

/**
 * Transport-agnostic cryptographic primitives for the owner-enrollment channel.
 *
 * This is an interface only: the core library stays free of mbedTLS so it can
 * be host-tested. The production implementation (MbedTlsSessionCrypto.h) is
 * compiled only for ESP32; tests inject a deterministic replacement.
 *
 * Every method returns false on failure. Callers must treat a false return as
 * "abort / fail-closed" and must never fall back to a weaker path.
 */
class SessionCrypto {
 public:
  virtual ~SessionCrypto() = default;

  /** Fill `out` (len bytes) with cryptographically secure random bytes. */
  virtual bool randomBytes(uint8_t* out, size_t len) = 0;

  /** Generate a fresh X25519 key pair. */
  virtual bool generateKeyPair(SessionKeyPair& out) = 0;

  /** Raw X25519 shared secret between our private key and the peer public key. */
  virtual bool sharedSecret(const uint8_t privateKey[kSessionPrivateKeyBytes],
                            const uint8_t peerPublicKey[kSessionPublicKeyBytes],
                            uint8_t secretOut[kSessionSharedSecretBytes]) = 0;

  /** HMAC-SHA256 over `data` with `key`. */
  virtual bool hmacSha256(const uint8_t* key, size_t keyLen, const uint8_t* data,
                          size_t dataLen, uint8_t macOut[kSessionMacBytes]) = 0;

  /** AES-256-GCM seal. `ciphertextOut` (plaintextLen) and `tagOut` (16). */
  virtual bool aeadSeal(const uint8_t key[kSessionKeyBytes],
                        const uint8_t nonce[kSessionNonceBytes], const uint8_t* aad,
                        size_t aadLen, const uint8_t* plaintext, size_t plaintextLen,
                        uint8_t* ciphertextOut, uint8_t tagOut[kSessionTagBytes]) = 0;

  /** AES-256-GCM open + authenticate. */
  virtual bool aeadOpen(const uint8_t key[kSessionKeyBytes],
                        const uint8_t nonce[kSessionNonceBytes], const uint8_t* aad,
                        size_t aadLen, const uint8_t* ciphertext, size_t ciphertextLen,
                        const uint8_t tag[kSessionTagBytes], uint8_t* plaintextOut) = 0;
};

}  // namespace esp32core
