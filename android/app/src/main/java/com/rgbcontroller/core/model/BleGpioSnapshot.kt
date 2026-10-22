package com.rgbcontroller.core.model

/**
 * Optional read-only BLE occupancy advertised by Compi V9 firmware.
 * It proves firmware leases at capture time, not electrical voltage or ownership.
 */
data class BleGpioPin(val number: Int, val state: Char) {
    val description: String get() = when (state) {
        'F' -> "Libre en firmware"
        'R' -> "Reservado por RGB"
        'H' -> "Salida manual HIGH"
        'L' -> "Salida manual LOW"
        'E' -> "Experimento activo"
        else -> "Recurso ocupado"
    }
}

object BleGpioSnapshot {
    private val expected = listOf(18, 19, 21, 22, 23, 25, 26, 27)
    fun parse(raw: String): List<BleGpioPin>? {
        if (raw.length > 96) return null
        val values = raw.split(',')
        if (values.size != expected.size + 1 || values.first() != "PINS") return null
        val result = values.drop(1).mapIndexed { index, item ->
            val parts = item.split(':')
            if (parts.size != 2 || parts[0].toIntOrNull() != expected[index] ||
                parts[1].length != 1 || parts[1][0] !in "FRHLEX") return null
            BleGpioPin(expected[index], parts[1][0])
        }
        return result
    }
}
