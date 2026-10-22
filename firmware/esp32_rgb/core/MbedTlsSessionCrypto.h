#pragma once

// Concrete SessionCrypto backed by mbedTLS (ESP-IDF 5.x / mbedTLS 3.6).
// Only compiled for the ESP32 target; host builds use the deterministic test
// double in tests/support. Never include mbedTLS into the portable core.

#if defined(ARDUINO) || defined(ESP_PLATFORM)

#include <mbedtls/ecp.h>
#include <mbedtls/gcm.h>
#include <mbedtls/md.h>
#include <esp_system.h>
#include <esp_random.h>

#include "SessionCrypto.h"

namespace esp32core {

class MbedTlsSessionCrypto final : public SessionCrypto {
 public:
  bool randomBytes(uint8_t* out, size_t len) override {
    if (out == nullptr && len != 0) return false;
    esp_fill_random(out, len);
    return true;
  }

  bool generateKeyPair(SessionKeyPair& out) override {
    mbedtls_ecp_group group;
    mbedtls_mpi secret;
    mbedtls_ecp_point publicPoint;
    mbedtls_ecp_group_init(&group);
    mbedtls_mpi_init(&secret);
    mbedtls_ecp_point_init(&publicPoint);

    bool ok = mbedtls_ecp_group_load(&group, MBEDTLS_ECP_DP_CURVE25519) == 0;
    if (ok) {
      ok = mbedtls_ecp_gen_keypair(&group, &secret, &publicPoint, &randomBridge,
                                   nullptr) == 0;
    }
    size_t length = 0;
    if (ok) {
      ok = mbedtls_ecp_point_write_binary(&group, &publicPoint,
                                          MBEDTLS_ECP_PF_UNCOMPRESSED, &length,
                                          out.publicKey, kSessionPublicKeyBytes) == 0 &&
           length == kSessionPublicKeyBytes;
    }
    if (ok) {
      ok = mbedtls_mpi_write_binary_le(&secret, out.privateKey,
                                       kSessionPrivateKeyBytes) == 0;
    }

    mbedtls_ecp_point_free(&publicPoint);
    mbedtls_mpi_free(&secret);
    mbedtls_ecp_group_free(&group);
    return ok;
  }

  bool sharedSecret(const uint8_t privateKey[kSessionPrivateKeyBytes],
                    const uint8_t peerPublicKey[kSessionPublicKeyBytes],
                    uint8_t secretOut[kSessionSharedSecretBytes]) override {
    mbedtls_ecp_group group;
    mbedtls_mpi secret;
    mbedtls_ecp_point peerPoint;
    mbedtls_ecp_point result;
    mbedtls_ecp_group_init(&group);
    mbedtls_mpi_init(&secret);
    mbedtls_ecp_point_init(&peerPoint);
    mbedtls_ecp_point_init(&result);

    bool ok = mbedtls_ecp_group_load(&group, MBEDTLS_ECP_DP_CURVE25519) == 0;
    if (ok) {
      ok = mbedtls_mpi_read_binary_le(&secret, privateKey,
                                      kSessionPrivateKeyBytes) == 0;
    }
    if (ok) {
      ok = mbedtls_ecp_point_read_binary(&group, &peerPoint, peerPublicKey,
                                         kSessionPublicKeyBytes) == 0;
    }
    if (ok) {
      ok = mbedtls_ecp_mul(&group, &result, &secret, &peerPoint, &randomBridge,
                           nullptr) == 0;
    }
    size_t length = 0;
    if (ok) {
      ok = mbedtls_ecp_point_write_binary(&group, &result,
                                          MBEDTLS_ECP_PF_UNCOMPRESSED, &length,
                                          secretOut, kSessionSharedSecretBytes) == 0 &&
           length == kSessionSharedSecretBytes;
    }

    mbedtls_ecp_point_free(&result);
    mbedtls_ecp_point_free(&peerPoint);
    mbedtls_mpi_free(&secret);
    mbedtls_ecp_group_free(&group);
    return ok;
  }

  bool hmacSha256(const uint8_t* key, size_t keyLen, const uint8_t* data,
                  size_t dataLen, uint8_t macOut[kSessionMacBytes]) override {
    const mbedtls_md_info_t* info = mbedtls_md_info_from_type(MBEDTLS_MD_SHA256);
    if (info == nullptr) return false;
    return mbedtls_md_hmac(info, key, keyLen, data, dataLen, macOut) == 0;
  }

  bool aeadSeal(const uint8_t key[kSessionKeyBytes],
                const uint8_t nonce[kSessionNonceBytes], const uint8_t* aad,
                size_t aadLen, const uint8_t* plaintext, size_t plaintextLen,
                uint8_t* ciphertextOut,
                uint8_t tagOut[kSessionTagBytes]) override {
    if (plaintextLen != 0 && (plaintext == nullptr || ciphertextOut == nullptr)) {
      return false;
    }
    mbedtls_gcm_context context;
    mbedtls_gcm_init(&context);
    bool ok = mbedtls_gcm_setkey(&context, MBEDTLS_CIPHER_ID_AES, key, 256) == 0;
    if (ok) {
      ok = mbedtls_gcm_crypt_and_tag(&context, MBEDTLS_GCM_ENCRYPT, plaintextLen,
                                     nonce, kSessionNonceBytes, aad, aadLen,
                                     plaintext, ciphertextOut, kSessionTagBytes,
                                     tagOut) == 0;
    }
    mbedtls_gcm_free(&context);
    return ok;
  }

  bool aeadOpen(const uint8_t key[kSessionKeyBytes],
                const uint8_t nonce[kSessionNonceBytes], const uint8_t* aad,
                size_t aadLen, const uint8_t* ciphertext, size_t ciphertextLen,
                const uint8_t tag[kSessionTagBytes],
                uint8_t* plaintextOut) override {
    if (ciphertextLen != 0 && (ciphertext == nullptr || plaintextOut == nullptr)) {
      return false;
    }
    mbedtls_gcm_context context;
    mbedtls_gcm_init(&context);
    bool ok = mbedtls_gcm_setkey(&context, MBEDTLS_CIPHER_ID_AES, key, 256) == 0;
    if (ok) {
      ok = mbedtls_gcm_auth_decrypt(&context, ciphertextLen, nonce,
                                    kSessionNonceBytes, aad, aadLen, tag,
                                    kSessionTagBytes, ciphertext,
                                    plaintextOut) == 0;
    }
    mbedtls_gcm_free(&context);
    return ok;
  }

 private:
  static int randomBridge(void*, unsigned char* buffer, size_t length) {
    esp_fill_random(buffer, length);
    return 0;
  }
};

}  // namespace esp32core

#endif  // ARDUINO || ESP_PLATFORM
