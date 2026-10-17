package com.rgbcontroller.core.security

/**
 * Transport-agnostic cryptographic primitives for the owner-enrollment channel.
 *
 * Mirrors `firmware/esp32_rgb/core/SessionCrypto.h`: X25519 + HMAC-SHA256. The
 * interface exists so the protocol logic stays testable and Android never talks
 * to BouncyCastle directly outside [BouncyCastleSessionCrypto]. Callers must
 * treat a thrown exception as "abort / fail-closed" and must never fall back to
 * a weaker path.
 */
class SessionKeyPair(
    val publicKey: ByteArray,
    val privateKey: ByteArray
) {
    init {
        require(publicKey.size == SessionCrypto.PUBLIC_KEY_BYTES) { "bad public key size" }
        require(privateKey.size == SessionCrypto.PRIVATE_KEY_BYTES) { "bad private key size" }
    }
}

interface SessionCrypto {
    companion object {
        const val PUBLIC_KEY_BYTES = 32
        const val PRIVATE_KEY_BYTES = 32
        const val SHARED_SECRET_BYTES = 32
        const val MAC_BYTES = 32
    }

    /** Cryptographically secure random bytes. */
    fun randomBytes(length: Int): ByteArray

    /** A fresh X25519 key pair. */
    fun generateKeyPair(): SessionKeyPair

    /** Raw X25519 shared secret between our private key and the peer public key. */
    fun sharedSecret(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray

    /** HMAC-SHA256 over [data] with [key]. */
    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray
}
