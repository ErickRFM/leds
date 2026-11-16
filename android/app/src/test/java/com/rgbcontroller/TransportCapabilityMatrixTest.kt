package com.rgbcontroller

import com.rgbcontroller.core.model.CapabilitySupport
import com.rgbcontroller.core.model.TransportCapabilityMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The matrix is the only surface that tells the user what Bluetooth and the lab
 * Wi-Fi can do. It fails loudly if a row starts asserting support the protocol
 * cannot back, because the firmware whitelist and the HTTP route table differ.
 */
class TransportCapabilityMatrixTest {
    private val rows = TransportCapabilityMatrix.rows
    private fun row(id: String) = rows.first { it.id == id }

    @Test fun everySupportedCellCitesItsFrameOrEndpoint() {
        assertTrue(TransportCapabilityMatrix.unsupportedClaims().isEmpty())
        assertTrue(TransportCapabilityMatrix.proofViolations().isEmpty())
    }

    @Test fun idsAreUniqueAndLabelsAreReadable() {
        assertEquals(rows.size, rows.map { it.id }.distinct().size)
        assertTrue(rows.all { it.label.isNotBlank() && it.group.isNotBlank() })
    }

    @Test fun bluetoothProofsAreRealGattFramePrefixes() {
        val accepted = setOf(
            "ENV,", "SET,", "FX,", "SNP,", "PIN,", "CPN,", "LDR,", "CRN,", "AUD,", "OLD,",
            "GPIO_STATUS", "PUBLIC_ID"
        )
        val cited = rows.mapNotNull { it.bleProof }.toSet()
        assertTrue("matriz cita marcos desconocidos: $cited", accepted.containsAll(cited))
    }

    @Test fun wifiProofsAreLabRoutesAndNothingElse() {
        assertTrue(rows.mapNotNull { it.wifiProof }.all { it.startsWith("/v2/") })
    }

    @Test fun otaStaysBlockedOnBothTransports() {
        val ota = row("ota")
        assertEquals(CapabilitySupport.UNAVAILABLE, ota.ble)
        assertEquals(CapabilitySupport.UNAVAILABLE, ota.wifi)
        assertNull(ota.bleProof)
        assertNull(ota.wifiProof)
        assertTrue(ota.note!!.contains("firmada"))
    }

    @Test fun craneMotionIsBluetoothOnlyWhileItsPinsAlsoRewireOverWifi() {
        assertEquals(CapabilitySupport.AVAILABLE, row("crane_motion").ble)
        assertEquals(CapabilitySupport.UNAVAILABLE, row("crane_motion").wifi)
        assertNull(row("crane_motion").wifiProof)
        assertEquals(CapabilitySupport.AVAILABLE, row("pin_rewire").wifi)
    }

    @Test fun rawGpioAdcAutomationAndWifiSetupHaveNoBluetoothVerb() {
        listOf("gpio_write", "adc_raw", "automation", "script", "lab_checks", "wifi_config")
            .forEach { id ->
                assertEquals("$id no debe prometer Bluetooth", CapabilitySupport.UNAVAILABLE, row(id).ble)
                assertNull(row(id).bleProof)
            }
    }

    @Test fun bluetoothRewireIsNarrowerThanTheLabRewire() {
        assertEquals(CapabilitySupport.LIMITED, row("pin_rewire").ble)
        assertTrue(row("pin_rewire").note!!.contains("dht11"))
        assertFalse("el BLE no acepta todo el catálogo",
            row("pin_rewire").ble == CapabilitySupport.AVAILABLE)
    }

    @Test fun telemetryAndStatusAreConfirmedOnBothTransports() {
        listOf("sensor_telemetry", "component_status", "ldr_calibration", "audio", "oled", "identity")
            .forEach { id ->
                assertEquals(CapabilitySupport.AVAILABLE, row(id).ble)
                assertEquals(CapabilitySupport.AVAILABLE, row(id).wifi)
            }
    }

    @Test fun identificationAloneNeverClaimsAuthorization() {
        assertTrue(row("identity").note!!.contains("autoriz"))
    }
}
