package com.rgbcontroller.core.ble

/** Exact request/ack pair for an owner-authenticated runtime-only LDR calibration. */
object BleLdrCalibrationProtocol {
    sealed interface Reply {
        val id: Int
        data class Applied(override val id: Int) : Reply
        data class Rejected(override val id: Int) : Reply
    }

    fun request(id: Int, dark: Int, bright: Int): String {
        require(id in 1..65535)
        require(dark in 0..4095 && bright in 0..4095 &&
            kotlin.math.abs(bright - dark) >= 32)
        return "LDR,$id,$dark,$bright"
    }

    fun reply(frame: String): Reply? {
        val parts = frame.split(',')
        if (parts.size != 3 || parts[0] != "LDR") return null
        val id = parts[2].toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        return when (parts[1]) {
            "OK" -> Reply.Applied(id)
            "REJECT" -> Reply.Rejected(id)
            else -> null
        }
    }
}
