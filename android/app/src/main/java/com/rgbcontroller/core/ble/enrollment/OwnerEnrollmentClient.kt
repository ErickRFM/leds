package com.rgbcontroller.core.ble.enrollment

import com.rgbcontroller.core.security.Hex
import com.rgbcontroller.core.security.SessionCrypto
import com.rgbcontroller.core.security.SessionKeyPair
import com.rgbcontroller.core.security.SessionKeySchedule

/**
 * Client side of the owner-enrollment handshake (Android is the owner).
 *
 * It only builds protocol lines and derives the proof; the board remains the
 * authority. Nothing here authorizes a command by itself. The `counter` must be
 * strictly increasing across reconnects and reboots, so callers persist
 * [lastCounter] and restore it before the next handshake.
 *
 * Wire format mirrors `firmware/esp32_rgb/core/OwnerEnrollmentFrames.h`:
 *   PAIR <code> <ownerId> <ownerPubKeyHex>
 *   BEGIN <clientNonceHex>
 *   CHALLENGE <deviceNonceHex> <devicePubKeyHex>   (board -> client)
 *   AUTH <clientNonceHex> <counter> <proofHex>
 */
class OwnerEnrollmentClient(
    private val crypto: SessionCrypto,
    val ownerId: String,
    private val ownerKeys: SessionKeyPair,
    var lastCounter: Int = 0,
) {
    private var clientNonceHex: String? = null
    private var pendingCounter: Int = 0

    val ownerPublicKeyHex: String get() = Hex.encode(ownerKeys.publicKey)

    /** True once the last AUTH was accepted and the counter advanced. */
    val authenticatedCounter: Int get() = lastCounter

    fun pairLine(code: String): String {
        require(code.length == ONE_TIME_CODE_LENGTH && code.all { it.isHexDigit() }) {
            "one-time code must be $ONE_TIME_CODE_LENGTH hex chars"
        }
        require(validOwnerId(ownerId)) { "invalid owner id" }
        return "PAIR ${code.lowercase()} $ownerId $ownerPublicKeyHex"
    }

    /** Starts a session; the returned line must be written before the challenge. */
    fun beginLine(): String {
        val hex = Hex.encode(crypto.randomBytes(SessionKeySchedule.NONCE_BYTES))
        clientNonceHex = hex
        return "BEGIN $hex"
    }

    /**
     * Builds the AUTH line for a parsed [EnrollmentReply.Challenge]. The counter
     * is [lastCounter] + 1 and is only committed by [onAuthenticated].
     */
    fun authLine(challenge: EnrollmentReply.Challenge): String {
        val clientHex = clientNonceHex ?: error("beginLine() must be called first")
        val deviceNonce = Hex.decode(challenge.deviceNonceHex, SessionKeySchedule.NONCE_BYTES)
            ?: error("invalid device nonce")
        val devicePublic = Hex.decode(challenge.devicePublicKeyHex, SessionCrypto.PUBLIC_KEY_BYTES)
            ?: error("invalid device public key")
        val clientNonce = Hex.decode(clientHex, SessionKeySchedule.NONCE_BYTES)
            ?: error("invalid client nonce")

        val counter = lastCounter + 1
        require(counter > 0) { "counter overflow" }
        pendingCounter = counter

        val output = SessionKeySchedule.derive(
            crypto, ownerKeys.privateKey, devicePublic, deviceNonce, clientNonce, counter,
        )
        return "AUTH $clientHex $counter ${Hex.encode(output.proof)}"
    }

    /** Commit the counter after the board replied OK to AUTH. */
    fun onAuthenticated() {
        if (pendingCounter > lastCounter) lastCounter = pendingCounter
        clientNonceHex = null
    }

    /** Drop any half-open handshake (disconnect / error). */
    fun reset() {
        clientNonceHex = null
    }

    companion object {
        const val ONE_TIME_CODE_LENGTH = 8

        private const val OWNER_ID_MAX = 32

        private fun validOwnerId(ownerId: String): Boolean =
            ownerId.isNotEmpty() && ownerId.length <= OWNER_ID_MAX &&
                ownerId.all { it.isLetterOrDigit() || it == '-' || it == '_' }
    }
}
