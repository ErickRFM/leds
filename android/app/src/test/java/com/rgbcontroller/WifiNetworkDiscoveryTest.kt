package com.rgbcontroller

import com.rgbcontroller.feature.devices.WifiNetworkCandidate
import com.rgbcontroller.feature.devices.WifiNetworkDiscovery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiNetworkDiscoveryTest {
    @Test fun genuineSoftApIsPrioritizedWithoutInventingOthers() {
        val data = listOf(
            WifiNetworkCandidate("School", -35),
            WifiNetworkCandidate("ESP32-Control-Lab", -75),
            WifiNetworkCandidate("ESP32-Control-Lab", -55),
            WifiNetworkCandidate("Other", -30)
        )
        val result = WifiNetworkDiscovery.present(data)
        assertEquals("ESP32-Control-Lab", result.first().ssid)
        assertEquals(-55, result.first().rssi)
        assertEquals(3, result.size)
    }

    @Test fun noScanEntriesMeansNoFabricatedDevice() {
        assertTrue(WifiNetworkDiscovery.present(emptyList()).isEmpty())
        assertTrue(WifiNetworkDiscovery.present(
            listOf(WifiNetworkCandidate("", -50), WifiNetworkCandidate("Fake", -150))
        ).isEmpty())
    }

    @Test fun sanitizesAndBoundsAndroidScanResults() {
        val input = (0..40).map { WifiNetworkCandidate("Network$it", -60) } +
            WifiNetworkCandidate("x".repeat(33), -20)
        assertEquals(16, WifiNetworkDiscovery.present(input).size)
        assertTrue(WifiNetworkDiscovery.present(input).none { it.ssid.length > 32 })
    }
}
