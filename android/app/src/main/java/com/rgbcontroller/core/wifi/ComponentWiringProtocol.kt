package com.rgbcontroller.core.wifi

import org.json.JSONObject

/**
 * Firmware GPIO authority: an HTTP 200 receipt alone NEVER confirms a pin
 * change. Only an authenticated /components/status readback with an ACTIVE
 * lease on the requested GPIO may complete the operation.
 */
object ComponentWiringProtocol {
    const val VERSION = 1
    val mutableComponents = setOf("dht22", "soil_moisture")

    fun firmwareId(componentId: String): String? = when (componentId) {
        "dht22" -> "sensor.dht22"
        "soil_moisture" -> "sensor.soil_moisture"
        else -> null
    }

    fun supported(capabilities: JSONObject, componentId: String): Boolean {
        if (capabilities.optInt("version", -1) != VERSION) return false
        val supported = capabilities.optJSONArray("supported") ?: return false
        return (0 until supported.length()).any { supported.optString(it) == componentId }
    }

    fun receiptMatches(
        receipt: JSONObject, componentId: String, pin: Int, requestId: String
    ): Boolean =
        receipt.optInt("version", -1) == VERSION &&
            receipt.optString("componentId") == componentId &&
            receipt.optString("requestId") == requestId &&
            receipt.optInt("pin", -1) == pin &&
            receipt.optString("outcome") in setOf("applied", "already_applied") &&
            receipt.optString("persistence") == "until_reboot" &&
            receipt.optLong("revision", -1) >= 0

    fun confirmedByFirmware(status: JSONObject, componentId: String, pin: Int): Boolean {
        if (status.optInt("version", -1) != VERSION ||
            status.optString("gpioAuthority") != "firmware"
        ) return false
        val id = firmwareId(componentId) ?: return false
        val components = status.optJSONArray("components") ?: return false
        for (index in 0 until components.length()) {
            val module = components.optJSONObject(index) ?: continue
            if (module.optString("id") != id) continue
            val actualPins = module.optJSONArray("pins") ?: return false
            return module.optBoolean("active", false) &&
                module.optString("state") == "active" &&
                actualPins.length() == 1 && actualPins.optInt(0, -1) == pin
        }
        return false
    }
}
