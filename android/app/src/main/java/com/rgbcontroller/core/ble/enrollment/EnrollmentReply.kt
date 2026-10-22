package com.rgbcontroller.core.ble.enrollment

import com.rgbcontroller.core.security.SessionCrypto
import com.rgbcontroller.core.security.SessionKeySchedule

/**
 * A parsed owner-enrollment reply from the board.
 *
 * Parsing is strict and fail-closed: a malformed line returns null so the caller
 * aborts rather than acting on a partial result. A [Challenge] always carries the
 * board's X25519 public key (SEG.1b-2c) needed to derive the shared secret.
 */
sealed class EnrollmentReply {
    object Ok : EnrollmentReply()
    data class Err(val reason: String) : EnrollmentReply()
    data class Challenge(
        val deviceNonceHex: String,
        val devicePublicKeyHex: String,
    ) : EnrollmentReply()

    companion object {
        private const val NONCE_HEX = SessionKeySchedule.NONCE_BYTES * 2
        private const val PUBLIC_KEY_HEX = SessionCrypto.PUBLIC_KEY_BYTES * 2

        fun parse(text: String): EnrollmentReply? {
            if (text.isEmpty()) return null
            val tokens = text.split(' ')
            if (tokens.any { it.isEmpty() }) return null
            return when (tokens[0]) {
                "OK" -> if (tokens.size == 1) Ok else null
                "ERR" -> if (tokens.size == 2 && validReason(tokens[1])) Err(tokens[1]) else null
                "CHALLENGE" -> if (tokens.size == 3 && isHex(tokens[1], NONCE_HEX) &&
                    isHex(tokens[2], PUBLIC_KEY_HEX)
                ) {
                    Challenge(tokens[1].lowercase(), tokens[2].lowercase())
                } else {
                    null
                }
                else -> null
            }
        }

        private fun isHex(text: String, length: Int): Boolean =
            text.length == length && text.all { it.isHexDigit() }

        private fun validReason(reason: String): Boolean =
            reason.isNotEmpty() && reason.length <= 32 &&
                reason.all { it.isLetterOrDigit() || it == '_' }
    }
}

internal fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
