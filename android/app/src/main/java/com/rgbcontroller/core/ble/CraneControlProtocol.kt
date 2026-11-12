package com.rgbcontroller.core.ble

/**
 * Correlated reply to a CRN command. A GATT write result only means the frame
 * entered the queue; this type is built exclusively from an owner-authorized
 * STATE notification, so the UI can separate delivery from firmware
 * confirmation.
 */
data class CraneControlReply(
    val requestId: Int,
    val accepted: Boolean,
    val baseDeg: Float?,
    val armDeg: Float?,
    val mode: String?,
    val moving: Boolean,
    val error: String?
)

object CraneControlProtocol {
    const val PREFIX = "CRN,"
    private const val MAX_REQUEST_ID = 65535

    /**
     * Accepts the two frames the firmware emits:
     * `CRN,<id>,OK,<base>,<arm>,LOCAL|REMOTE,<moving>`
     * `CRN,<id>,ERR,<reason>` and the rejected variant that appends state.
     */
    fun parse(frame: String): CraneControlReply? {
        if (!frame.startsWith(PREFIX)) return null
        val parts = frame.split(',')
        if (parts.size < 3) return null
        val requestId = parts[1].toIntOrNull()
            ?.takeIf { it in 1..MAX_REQUEST_ID } ?: return null
        return when (parts[2]) {
            "OK" -> parseState(parts, offset = 3)?.let { state ->
                CraneControlReply(
                    requestId = requestId,
                    accepted = true,
                    baseDeg = state.baseDeg,
                    armDeg = state.armDeg,
                    mode = state.mode,
                    moving = state.moving,
                    error = null
                )
            }
            "ERR" -> {
                val reason = parts.getOrNull(3)?.takeIf { it.isNotBlank() } ?: return null
                val state = parseState(parts, offset = 4)
                CraneControlReply(
                    requestId = requestId,
                    accepted = false,
                    baseDeg = state?.baseDeg,
                    armDeg = state?.armDeg,
                    mode = state?.mode,
                    moving = state?.moving ?: false,
                    error = reason
                )
            }
            else -> null
        }
    }

    private data class State(
        val baseDeg: Float,
        val armDeg: Float,
        val mode: String,
        val moving: Boolean
    )

    private fun parseState(parts: List<String>, offset: Int): State? {
        if (parts.size != offset + 4) return null
        val base = parts[offset].toFloatOrNull()
            ?.takeIf { it.isFinite() && it in 0f..180f } ?: return null
        val arm = parts[offset + 1].toFloatOrNull()
            ?.takeIf { it.isFinite() && it in 0f..180f } ?: return null
        val mode = parts[offset + 2].takeIf { it == "LOCAL" || it == "REMOTE" }
            ?: return null
        val moving = when (parts[offset + 3]) {
            "0" -> false
            "1" -> true
            else -> return null
        }
        return State(base, arm, mode, moving)
    }
}
