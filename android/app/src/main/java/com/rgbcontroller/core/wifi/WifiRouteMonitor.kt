package com.rgbcontroller.core.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/**
 * Publishes whether the phone actually holds a route to the ESP32 SoftAP.
 * The lab network has no Internet, so Android never validates it as the default
 * network; a Wi-Fi transport request is required to observe it at all.
 */
class WifiRouteMonitor(
    context: Context,
    private val onRouteAvailable: (Boolean) -> Unit
) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val labNetworks = mutableSetOf<Network>()
    private var registered = false

    private val request = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .build()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh(network)
        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) =
            refresh(network)
        override fun onLost(network: Network) = forget(network)
    }

    fun start() {
        if (registered || manager == null) return
        manager.registerNetworkCallback(request, callback)
        registered = true
    }

    fun stop() {
        if (!registered) return
        manager?.unregisterNetworkCallback(callback)
        registered = false
        labNetworks.clear()
        onRouteAvailable(false)
    }

    private fun refresh(network: Network) {
        val link = manager?.getLinkProperties(network) ?: return
        val wifi = manager.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val eligible = LabNetworkPolicy.isEligibleLink(
            wifi,
            link.routes.mapNotNull { it.gateway?.hostAddress },
            link.linkAddresses.mapNotNull { it.address?.hostAddress }
        )
        if (eligible) remember(network) else forget(network)
    }

    private fun remember(network: Network) {
        if (labNetworks.add(network)) onRouteAvailable(true)
    }

    private fun forget(network: Network) {
        if (labNetworks.remove(network) && labNetworks.isEmpty()) onRouteAvailable(false)
    }
}