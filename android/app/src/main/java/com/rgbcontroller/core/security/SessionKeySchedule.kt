package com.rgbcontroller.core.security

/**
 * Derives the session key and the enrollment proof from the ECDH shared secret.
 *
 * Byte-for-byte mirror of `firmware/esp32_rgb/core/SessionKeySchedule.h`: both
 * sides run this with their own private key, the peer public key and both nonces
 * and must reach identical outputs. The session key binds both nonces; the proof
 * additionally binds the monotonic counter in big-endian, so a replayed or
 * re-ordered proof never validates.
 */
object SessionKeySchedule {
    const val NONCE_BYTES = 16

    private const val SESSION_LABEL = "compi/1 session"
    private const val PROOF_LABEL = "compi/1 proof"

    class Output(val sessionKey: ByteArray, val proof: ByteArray)

    fun derive(
        crypto: SessionCrypto,
        ourPrivateKey: ByteArray,
        peerPublicKey: ByteArray,
        deviceNonce: ByteArray,
        clientNonce: ByteArray,
        counter: Int,
    ): Output {
        require(deviceNonce.size == NONCE_BYTES) { "device nonce must be $NONCE_BYTES bytes" }
        require(clientNonce.size == NONCE_BYTES) { "client nonce must be $NONCE_BYTES bytes" }

        val secret = crypto.sharedSecret(ourPrivateKey, peerPublicKey)

        val sessionInput = ByteArray(1 + SESSION_LABEL.length + 2 * NONCE_BYTES)
        sessionInput[0] = 'C'.code.toByte()
        SESSION_LABEL.encodeToByteArray().copyInto(sessionInput, 1)
        deviceNonce.copyInto(sessionInput, 1 + SESSION_LABEL.length)
        clientNonce.copyInto(sessionInput, 1 + SESSION_LABEL.length + NONCE_BYTES)
        val sessionKey = crypto.hmacSha256(secret, sessionInput)

        val proofInput = ByteArray(1 + PROOF_LABEL.length + 2 * NONCE_BYTES + 4)
        proofInput[0] = 'P'.code.toByte()
        PROOF_LABEL.encodeToByteArray().copyInto(proofInput, 1)
        deviceNonce.copyInto(proofInput, 1 + PROOF_LABEL.length)
        clientNonce.copyInto(proofInput, 1 + PROOF_LABEL.length + NONCE_BYTES)
        val counterSlot = 1 + PROOF_LABEL.length + 2 * NONCE_BYTES
        proofInput[counterSlot] = ((counter ushr 24) and 0xFF).toByte()
        proofInput[counterSlot + 1] = ((counter ushr 16) and 0xFF).toByte()
        proofInput[counterSlot + 2] = ((counter ushr 8) and 0xFF).toByte()
        proofInput[counterSlot + 3] = (counter and 0xFF).toByte()
        val proof = crypto.hmacSha256(sessionKey, proofInput)

        return Output(sessionKey, proof)
    }

    /** Constant-time comparison of two equal-length byte arrays. */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].toInt() xor b[i].toInt())
        }
        return diff == 0
    }
}
