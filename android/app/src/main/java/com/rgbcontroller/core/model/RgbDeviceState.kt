package com.rgbcontroller.core.model

data class RgbDeviceState(
    val red: Int,
    val green: Int,
    val blue: Int,
    val brightness: Int,
    val power: Boolean,
    val effect: String = "NONE",
    val speed: Int = 100
) {
    companion object {
        fun parse(frame: String): RgbDeviceState? {
            val parts = frame.trim().split(',')
            if (parts.size != 6 && parts.size != 8) return null
            if (parts[0] != "STATE") return null
            val values = parts.subList(1, 5).map { it.toIntOrNull() ?: return null }
            if (values.any { it !in 0..255 }) return null
            val power = when (parts[5]) {
                "1" -> true
                "0" -> false
                else -> return null
            }
            val effect = if (parts.size == 8) parts[6] else "NONE"
            if (effect !in setOf("NONE", "RAINBOW", "BREATHE", "FADE")) return null
            val speed = if (parts.size == 8) parts[7].toIntOrNull() ?: return null else 100
            if (speed !in 10..1000) return null
            return RgbDeviceState(values[0], values[1], values[2], values[3], power, effect, speed)
        }
    }
}
