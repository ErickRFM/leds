package com.rgbcontroller

import com.rgbcontroller.core.model.*
import org.junit.Assert.*
import org.junit.Test

class DeviceCapabilityPolicyTest {
    @Test fun legacyBleOnlyRgbAndOptionalPinReads() {
        for (feature in listOf(CompiOperation.READ_RGB, CompiOperation.SET_RGB, CompiOperation.EFFECT_RGB)) {
            assertTrue(DeviceCapabilityPolicy.check(feature, CompiChannel.BLE_V1, true).allowed)
        }
        assertFalse(DeviceCapabilityPolicy.check(CompiOperation.READ_GPIO, CompiChannel.BLE_V1, true).allowed)
        assertTrue(DeviceCapabilityPolicy.check(CompiOperation.READ_GPIO, CompiChannel.BLE_V1,
            true, gpioTelemetryAvailable = true).allowed)
        for (feature in listOf(CompiOperation.WRITE_GPIO, CompiOperation.READ_ADC,
            CompiOperation.RUN_GPIO_SCRIPT, CompiOperation.RUN_RGB_SCRIPT,
            CompiOperation.CONFIGURE_WIFI, CompiOperation.AUTOMATION, CompiOperation.FIRMWARE_UPDATE)) {
            assertFalse(DeviceCapabilityPolicy.check(feature, CompiChannel.BLE_V1, true).allowed)
        }
    }
    @Test fun wifiLabRequiresDebugAndNoFirmwareFlash() {
        assertTrue(DeviceCapabilityPolicy.check(CompiOperation.WRITE_GPIO, CompiChannel.WIFI_LAB, true, true).allowed)
        assertFalse(DeviceCapabilityPolicy.check(CompiOperation.WRITE_GPIO, CompiChannel.WIFI_LAB, true, false).allowed)
        assertFalse(DeviceCapabilityPolicy.check(CompiOperation.FIRMWARE_UPDATE, CompiChannel.WIFI_LAB, true).allowed)
        assertFalse(DeviceCapabilityPolicy.check(CompiOperation.READ_RGB, CompiChannel.WIFI_LAB, false).allowed)
    }
    @Test fun secureBleLabelAloneDoesNotGrantOwnerPermissions() {
        assertFalse(DeviceCapabilityPolicy.check(CompiOperation.WRITE_GPIO, CompiChannel.AUTHENTICATED_BLE,
            true, authorizedOwner = true).allowed)
        assertFalse(DeviceCapabilityPolicy.check(CompiOperation.RUN_GPIO_SCRIPT, CompiChannel.AUTHENTICATED_BLE,
            true, secureBleAvailable = true).allowed)
        assertTrue(DeviceCapabilityPolicy.check(CompiOperation.RUN_GPIO_SCRIPT, CompiChannel.AUTHENTICATED_BLE,
            true, authorizedOwner = true, secureBleAvailable = true).allowed)
    }
}
