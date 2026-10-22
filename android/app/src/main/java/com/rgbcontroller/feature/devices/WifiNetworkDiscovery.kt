package com.rgbcontroller.feature.devices

/** Actual Android scan evidence. An SSID alone is never proof of an ESP32. */
data class WifiNetworkCandidate(val ssid: String, val rssi: Int)

object WifiNetworkDiscovery {
    const val COMPI_SOFT_AP = "ESP32-Control-Lab"

    /** Deduplicated, bounded scan results, prioritizing the known lab SSID. */
    fun present(candidates: List<WifiNetworkCandidate>): List<WifiNetworkCandidate> =
        candidates.asSequence()
            .filter { it.ssid.isNotBlank() && it.ssid.length <= 32 && it.rssi in -127..0 }
            .groupBy { it.ssid }
            .mapNotNull { (_, entries) -> entries.maxByOrNull { it.rssi } }
            .sortedWith(
                compareByDescending<WifiNetworkCandidate> { it.ssid == COMPI_SOFT_AP }
                    .thenByDescending { it.rssi }
                    .thenBy { it.ssid }
            )
            .take(16)
            .toList()
}
