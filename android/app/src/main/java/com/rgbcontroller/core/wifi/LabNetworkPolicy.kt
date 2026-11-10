package com.rgbcontroller.core.wifi

/** A debug SoftAP request must never fall back to cellular or a random Wi-Fi router. */
object LabNetworkPolicy {
    const val EXPECTED_GATEWAY = "192.168.4.1"
    private const val LAB_SUBNET_PREFIX = "192.168.4."

    fun isEligible(isWifi: Boolean, gateways: List<String>) =
        isWifi && gateways.contains(EXPECTED_GATEWAY)

    /** A link address inside the SoftAP subnet, ignoring any CIDR suffix. */
    fun isLabSubnetAddress(address: String): Boolean {
        if (!address.startsWith(LAB_SUBNET_PREFIX)) return false
        val host = address.substring(LAB_SUBNET_PREFIX.length).substringBefore('/')
        val octet = host.toIntOrNull() ?: return false
        return octet in 1..254
    }

    /**
     * The SoftAP has no Internet, so the phone may never mark it as the validated
     * default network. Route evidence alone proves the lab link is usable.
     */
    fun isEligibleLink(isWifi: Boolean, gateways: List<String>, linkAddresses: List<String>) =
        isWifi && (gateways.contains(EXPECTED_GATEWAY) || linkAddresses.any(::isLabSubnetAddress))
}
