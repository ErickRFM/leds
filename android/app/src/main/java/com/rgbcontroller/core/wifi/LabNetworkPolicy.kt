package com.rgbcontroller.core.wifi

/** A debug SoftAP request must never fall back to cellular or a random Wi-Fi router. */
object LabNetworkPolicy {
    const val EXPECTED_GATEWAY = "192.168.4.1"
    fun isEligible(isWifi: Boolean, gateways: List<String>) =
        isWifi && gateways.contains(EXPECTED_GATEWAY)
}
