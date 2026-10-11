package com.rgbcontroller.core.model

import org.json.JSONObject

/**
 * Private per-board factory label for the *existing* V3 debug SoftAP.
 * It is not BLE pairing or proof of device ownership.
 * Never persist in DataStore, logcat, crash reports or exported projects.
 */
class FactoryLabCredentials private constructor(
    val ssid: String,
    val wifiPassword: String,
    val controlKey: String
) {
    override fun toString(): String = "FactoryLabCredentials([REDACTED])"

    companion object {
        private val hex16 = Regex("[0-9a-f]{16}")
        fun parse(source: String): FactoryLabCredentials? {
            if (source.toByteArray(Charsets.UTF_8).size !in 1..1024) return null
            return try {
                val payload = JSONObject(source)
                if (payload.optString("ssid") != "ESP32-Control-Lab") return null
                val password = payload.optString("password")
                val key = payload.optString("deviceKey")
                if (!hex16.matches(password) || !hex16.matches(key) ||
                    password == key) return null
                FactoryLabCredentials("ESP32-Control-Lab", password, key)
            } catch (_: Exception) { null }
        }
    }
}
