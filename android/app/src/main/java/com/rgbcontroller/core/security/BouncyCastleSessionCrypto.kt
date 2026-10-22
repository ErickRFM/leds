package com.rgbcontroller.core.security

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.security.SecureRandom

/**
 * BouncyCastle-backed [SessionCrypto].
 *
 * BouncyCastle is used instead of JCA because X25519 (`KeyAgreement "XDH"`) only
 * exists on Android 13 / API 33+, while this app supports API 26+. Every method
 * throws on failure so callers can fail closed.
 */
class BouncyCastleSessionCrypto(
    private val random: SecureRandom = SecureRandom()
) : SessionCrypto {

    override fun randomBytes(length: Int): ByteArray {
        require(length > 0) { "length must be positive" }
        return ByteArray(length).also { random.nextBytes(it) }
    }

    override fun generateKeyPair(): SessionKeyPair {
        val privateKey = X25519PrivateKeyParameters(random)
        val publicKey = privateKey.generatePublicKey()
        return SessionKeyPair(publicKey.encoded, privateKey.encoded)
    }

    override fun sharedSecret(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray {
        require(privateKey.size == SessionCrypto.PRIVATE_KEY_BYTES) { "bad private key size" }
        require(peerPublicKey.size == SessionCrypto.PUBLIC_KEY_BYTES) { "bad public key size" }
        val agreement = X25519Agreement()
        agreement.init(X25519PrivateKeyParameters(privateKey, 0))
        val secret = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(X25519PublicKeyParameters(peerPublicKey, 0), secret, 0)
        return secret
    }

    override fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = HMac(SHA256Digest())
        mac.init(KeyParameter(key))
        mac.update(data, 0, data.size)
        val out = ByteArray(mac.macSize)
        mac.doFinal(out, 0)
        return out
    }
}
