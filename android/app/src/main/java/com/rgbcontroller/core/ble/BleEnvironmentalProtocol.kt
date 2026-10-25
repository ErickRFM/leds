package com.rgbcontroller.core.ble

/** Exact BLE environmental frames. Owner authentication is enforced by both ends. */
object BleEnvironmentalProtocol {
    enum class Action(val frame: String) {
        GET("ENV,GET"), START("ENV,START"), STOP("ENV,STOP")
    }

    enum class StateEnum {
        ENABLED, RUNNING, SUSPENDED, CONFLICT, ERROR, DISABLED
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
        if (frame == "ENV,ON") {
            return Reply.State(EnvironmentalRgbSnapshot(enabled = true, state = StateEnum.ENABLED, reason = "AWAITING_STATUS"))
        }
        if (frame == "ENV,OFF") {
            return Reply.State(EnvironmentalRgbSnapshot(enabled = false, state = StateEnum.DISABLED, reason = "DISABLED"))
        }
        if (frame == "ENV,BUSY") {
            return Reply.Busy
        }
        if (frame.startsWith("ENV,STATE,")) {
            val parts = frame.split(",")
            if (parts.size >= 11) {
                val enabled = parts[2] == "ON" || parts[2] == "ENABLED" || parts[2] == "1"
                val stateStr = parts[3]
                val state = runCatching { StateEnum.valueOf(stateStr) }.getOrDefault(
                    if (enabled) StateEnum.ENABLED else StateEnum.DISABLED
                )
                val reason = parts[4]
                val band = parts[5]
                val red = parts[6].toIntOrNull() ?: 0
                val green = parts[7].toIntOrNull() ?: 0
                val blue = parts[8].toIntOrNull() ?: 0
                val brightness = parts[9].toIntOrNull() ?: 80
                val humidityUsed = parts[10] == "1" || parts[10].lowercase() == "true"
                val lightUsed = parts.getOrNull(11)?.let { it == "1" || it.lowercase() == "true" } ?: false
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
