package com.rgbcontroller.core.ble

object BleSensorPinProtocol {
    enum class Sensor(val code: String, val firmwareId: String) {
        DHT11("D", "sensor.dht11"), LDR("L", "sensor.ldr_light")
    }
    enum class Outcome { APPLIED, REPLAYED, BUSY, REJECTED }
    data class Receipt(val token: String, val outcome: Outcome)
    private val nonce = Regex("[0-9a-fA-F]{16}")
    fun request(token: String, sensor: Sensor, from: Int, to: Int): String {
        require(nonce.matches(token))
        require(from in 0..39 && to in 0..39)
        return "PIN,$token,${sensor.code},$from,$to"
    }
    fun receipt(frame: String): Receipt? {
        if (frame.length > 34) return null
        val parts = frame.split(',')
        if (parts.size != 3 || parts[0] != "PIN" || !nonce.matches(parts[1])) return null
        val outcome = Outcome.entries.firstOrNull { it.name == parts[2] } ?: return null
        return Receipt(parts[1], outcome)
    }
}
