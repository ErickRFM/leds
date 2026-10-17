package com.rgbcontroller

import com.rgbcontroller.core.ble.BleDiscoveryPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleDiscoveryPolicyTest {
    private fun matches(
        adv: String? = null, cached: String? = null,
        service: Boolean = false, address: String? = null,
        previous: String? = null
    ) = BleDiscoveryPolicy.isCandidate(adv, cached, service, address, previous)

    @Test fun legacyFirmwareAndCompiNamesAreRecognizedWithoutServiceInAdvertisement() {
        assertTrue(matches(adv = "RGB-ESP32"))
        assertTrue(matches(adv = "Compi-ESP32"))
        assertTrue(matches(cached = " rgb-esp32 "))
        assertTrue(matches(cached = "COMPI-ESP32"))
    }

    @Test fun uuidRecognizesNamelessPeripheralButDoesNotGrantTrust() {
        assertTrue(matches(service = true))
        assertTrue(matches(adv = "Unknown", service = true))
    }

    @Test fun previousMacAllowsRecoveryOfNamelessBoard() {
        assertTrue(matches(address = "AA:BB:CC:DD:FA:9A", previous = "aa:bb:cc:dd:fa:9a"))
        assertFalse(matches(address = "AA:BB:CC:DD:FA:9B", previous = "aa:bb:cc:dd:fa:9a"))
    }

    @Test fun unrelatedOrUnidentifiedBleDevicesStayHidden() {
        assertFalse(matches(adv = "Headphones"))
        assertFalse(matches(cached = "Random peripheral"))
        assertFalse(matches())
        assertFalse(matches(address = null, previous = "AA:BB:CC:DD:FA:9A"))
    }
}
