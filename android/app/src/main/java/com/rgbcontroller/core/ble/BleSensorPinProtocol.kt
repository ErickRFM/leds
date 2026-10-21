package com.rgbcontroller.core.ble

/** GPIO changes are read-back verified against firmware's active leases. */
object BleSensorPinProtocol {
    enum class Sensor(val code: String, val firmwareId: String) {
        DHT11("D", "sensor.dht11"),
        LDR("L", "sensor.ldr_light")
    }
    enum class Outcome { APPLIED, REPLAYED, BUSY, REJECTED }
    data class Receipt(val token: String, val outcome: Outcome)

    fun request(token: String, sensor: Sensor, from: Int, to: Int): String {
        require(token.matches(Regex("[0-9a-fA-F]{8}")))
        require(from in 0..39 && to in 0..39)
        return "PIN,$token,${sensor.code},$from,$to"
    }

    fun receipt(frame: String): Receipt? {
        val parts = frame.split(',')
        if (parts.size != 3 || parts[0] != "PIN" ||
            !parts[2].matches(Regex("[0-9a-fA-F]{8}"))) return null
        val outcome = when (parts[1]) {
            "OK" -> Outcome.APPLIED
            "REPLAY" -> Outcome.REPLAYED
            "BUSY" -> Outcome.BUSY
            "REJECT" -> Outcome.REJECTED
            else -> return null
        }
        return Receipt(parts[2], outcome)
    }
}
