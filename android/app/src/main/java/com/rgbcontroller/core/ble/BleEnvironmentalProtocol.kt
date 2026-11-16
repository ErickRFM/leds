package com.rgbcontroller.core.ble

/** Exact BLE environmental frames. Owner authentication is enforced by both ends. */
object BleEnvironmentalProtocol {
    enum class Action(val frame: String) {
        GET("ENV,GET"), START("ENV,START"), STOP("ENV,STOP")
    }

    /** UNKNOWN keeps an unrecognized firmware state from looking like success. */
    enum class StateEnum {
        RUNNING, SUSPENDED, CONFLICT, ERROR, DISABLED, UNKNOWN
    }

    data class EnvironmentalRgbSnapshot(
        val enabled: Boolean,
        val state: StateEnum,
        val reason: String,
        val band: String = "Comfort",
        val red: Int = 0,
        val green: Int = 0,
        val blue: Int = 0,
        val brightness: Int = 0,
        val humidityUsed: Boolean = false,
        val lightUsed: Boolean = false
    )

    sealed interface Reply {
        data class State(val snapshot: EnvironmentalRgbSnapshot) : Reply
        data object Busy : Reply
    }

    fun parseReply(frame: String): Reply? {
        if (frame == "ENV,BUSY") {
            return Reply.Busy
        }
        if (frame.startsWith("ENV,STATE,")) {
            val parts = frame.split(",")
            // encodeBleEnvironmentalStatus always emits twelve fields. A shorter
            // frame is a truncated notification, so guessing the missing fields
            // would report a colour or a band the ESP32 never applied.
            if (parts.size >= 12) {
                val enabled = parts[2] == "ON"
                val state = StateEnum.entries.firstOrNull { it.name == parts[3] }
                    ?: StateEnum.UNKNOWN
                val reason = parts[4]
                val band = parts[5]
                val red = parts[6].toIntOrNull() ?: 0
                val green = parts[7].toIntOrNull() ?: 0
                val blue = parts[8].toIntOrNull() ?: 0
                val brightness = parts[9].toIntOrNull() ?: 0
                val humidityUsed = parts[10] == "1"
                val lightUsed = parts[11] == "1"
                return Reply.State(
                    EnvironmentalRgbSnapshot(
                        enabled = enabled,
                        state = state,
                        reason = reason,
                        band = band,
                        red = red,
                        green = green,
                        blue = blue,
                        brightness = brightness,
                        humidityUsed = humidityUsed,
                        lightUsed = lightUsed
                    )
                )
            }
        }
        return null
    }
}
