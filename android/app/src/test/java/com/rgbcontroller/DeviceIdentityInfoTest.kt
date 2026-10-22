package com.rgbcontroller

import com.rgbcontroller.core.model.DeviceIdentityInfo
import org.junit.Assert.*
import org.junit.Test

class DeviceIdentityInfoTest {
    private val initial = DeviceIdentityInfo(
        4, "1234ABCDDEADBEEF", secureProvisioning = false,
        legacyBleWriteUnauthenticated = true, ownerBound = false
    )

    @Test fun legacyBleIsNeverMislabelledAsPaired() {
        assertTrue(initial.validId)
        assertTrue(initial.report.contains("NO disponible"))
        assertFalse(initial.report.contains("autorizado"))
    }

    @Test fun malformedOrWrongVersionFailsValidation() {
        assertFalse(initial.copy(deviceId = "1234abcdDEADBEEF").validId)
        assertFalse(initial.copy(deviceId = "DEADBEEF").validId)
        assertFalse(initial.copy(schemaVersion = 1).validId)
    }

    @Test fun capabilityFlagsAloneAreNotProofOfOwner() {
        val claimed = initial.copy(
            secureProvisioning = true, legacyBleWriteUnauthenticated = false,
            ownerBound = true
        )
        assertTrue(claimed.report.contains("requieren verificación"))
    }
}
