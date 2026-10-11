package com.rgbcontroller.core.model

/** Exact RGB V1 command contract shared by Android BLE and local Wi-Fi transports. */
object RgbDeviceCommand {
    fun set(r: Int, g: Int, b: Int, brightness: Int): String =
        ColorUtils.serializeSetCommand(r, g, b, brightness)

    fun effect(name: String, speed: Int): String {
        require(name in setOf("RAINBOW", "BREATHE", "FADE"))
        require(speed in 10..1000)
        return "FX," + name + "," + speed
    }

    fun allowed(command: String): Boolean {
        if (command.isEmpty() || command.length > 48) return false
        if (command in setOf("ON", "OFF", "GET", "FX,STOP")) return true
        val fields = command.split(',')
        if (fields.size == 5 && fields[0] == "SET") {
            return fields.drop(1).all {
                it.isNotEmpty() && it.all(Char::isDigit) &&
                    it.toIntOrNull()?.let { value -> value in 0..255 } == true
            }
        }
        if (fields.size == 3 && fields[0] == "FX" &&
            fields[1] in setOf("RAINBOW", "BREATHE", "FADE")) {
            return fields[2].isNotEmpty() && fields[2].all(Char::isDigit) &&
                fields[2].toIntOrNull()?.let { it in 10..1000 } == true
        }
        return false
    }
}
