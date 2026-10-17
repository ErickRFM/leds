package com.rgbcontroller.core.security

/**
 * Strict lowercase hex codec for the owner-enrollment protocol.
 *
 * Decoding fails closed: odd length, wrong size or any non-hex character yields
 * null. Encoding always produces lowercase to match the firmware contract.
 */
object Hex {
    private const val DIGITS = "0123456789abcdef"

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            out.append(DIGITS[v ushr 4])
            out.append(DIGITS[v and 0x0F])
        }
        return out.toString()
    }

    fun decode(text: String, expectedBytes: Int): ByteArray? {
        if (text.length != expectedBytes * 2) return null
        val out = ByteArray(expectedBytes)
        for (i in 0 until expectedBytes) {
            val hi = nibble(text[2 * i]) ?: return null
            val lo = nibble(text[2 * i + 1]) ?: return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    private fun nibble(c: Char): Int? = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> null
    }
}
