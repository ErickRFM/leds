package com.rgbcontroller.core.ble

/**
 * BLE discovery never grants owner privileges. Accept the firmware's current
 * and legacy names, the advertised service UUID or a previously selected
 * address. GATT service/characteristics must still be verified on connection.
 */
object BleDiscoveryPolicy {
    const val EXPECTED_NAME = "RGB-ESP32"
    val KNOWN_NAMES = setOf("RGB-ESP32", "COMPI-ESP32")

    fun isCandidate(
        advertisedName: String?,
        cachedName: String?,
        hasServiceUuid: Boolean,
        address: String?,
        rememberedAddress: String?
    ): Boolean {
        if (hasServiceUuid) return true
        if (listOfNotNull(advertisedName, cachedName).any {
            it.trim().uppercase(java.util.Locale.ROOT) in KNOWN_NAMES
        }) return true
        return !address.isNullOrBlank() &&
            !rememberedAddress.isNullOrBlank() &&
            address.equals(rememberedAddress, ignoreCase = true)
    }
}

data class BleScanStatistics(
    val advertisements: Int = 0,
    val compatibleDevices: Int = 0,
    val completed: Boolean = false,
    val failureCode: Int? = null
)
