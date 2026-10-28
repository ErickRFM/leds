package com.rgbcontroller

import com.rgbcontroller.core.components.model.ComponentRuntimeStatus
import com.rgbcontroller.core.components.registry.PinAssignmentPolicy
import com.rgbcontroller.core.components.status.LocalDesiredComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** No Wi-Fi/BLE/real hardware required: these invariants protect GPIO drafts. */
class PinAssignmentPolicyTest {
    @Test fun everyDefaultFitsItsSignal() {
        for (profile in PinAssignmentPolicy.profiles) {
            assertTrue(profile.componentId, PinAssignmentPolicy.validate(profile.componentId, profile.defaults).isEmpty())
            assertEquals(profile.signals.size, profile.defaults.size)
        }
    }

    @Test fun ldrUsesAvailableGpio32ByDefault() {
        assertEquals(listOf(32), PinAssignmentPolicy.profile("ldr_light")!!.defaults)
        assertTrue(PinAssignmentPolicy.validate("ldr_light", listOf(32)).isEmpty())
    }

    @Test fun rgbUsesThreeDistinctPwmOutputs() {
        assertEquals(listOf(27, 25, 26), PinAssignmentPolicy.profile("led_rgb")!!.defaults)
        assertTrue(PinAssignmentPolicy.validate("led_rgb", listOf(27, 25, 26)).isEmpty())
        assertFalse(PinAssignmentPolicy.validate("led_rgb", listOf(27, 27, 26)).isEmpty())
        assertFalse(PinAssignmentPolicy.validate("led_rgb", listOf(27, 25)).isEmpty())
        assertFalse(PinAssignmentPolicy.validate("led_rgb", listOf(34, 25, 26)).isEmpty())
    }

    @Test fun singleAdc1AndInputOnlyPinsAreNotPwm() {
        assertTrue(PinAssignmentPolicy.validate("soil_moisture", listOf(34)).isEmpty())
        assertFalse(PinAssignmentPolicy.validate("soil_moisture", listOf(25)).isEmpty())
        assertFalse(PinAssignmentPolicy.validate("soil_moisture", listOf(32,33,34,35,36,39)).isEmpty())
        assertFalse(PinAssignmentPolicy.validate("actuator_relay", listOf(34)).isEmpty())
        assertFalse(PinAssignmentPolicy.validate("dht22", listOf(34)).isEmpty())
        assertFalse(PinAssignmentPolicy.validate("dht22", listOf(6)).isEmpty()) // flash pin
        assertFalse(PinAssignmentPolicy.validate("dht22", listOf(25)).isEmpty()) // RGB reserved
        assertTrue(PinAssignmentPolicy.validate("dht22", listOf(16)).isEmpty()) // firmware output
        assertFalse(PinAssignmentPolicy.validate("dht22", listOf(0)).isEmpty()) // strap
        assertFalse(PinAssignmentPolicy.validate("dht22", listOf(23)).isEmpty()) // button
        assertFalse(PinAssignmentPolicy.validate("dht22", listOf(33)).isEmpty()) // DFPlayer BUSY
    }

    @Test fun oledRequiresDifferentSdaAndScl() {
        assertTrue(PinAssignmentPolicy.validate("ssd1306_oled", listOf(21, 22)).isEmpty())
        assertFalse(PinAssignmentPolicy.validate("ssd1306_oled", listOf(21, 21)).isEmpty())
    }

    @Test fun rejectsPinUsedByAnotherLocalModule() {
        val existing = listOf(LocalDesiredComponent("dht22", "sensor", listOf(32)))
        assertTrue(PinAssignmentPolicy.validate("soil_moisture", listOf(34), existing).isEmpty())
        assertFalse(PinAssignmentPolicy.validate("soil_moisture", listOf(32), existing).isEmpty())
        assertTrue(PinAssignmentPolicy.validate("dht22", listOf(32), existing).isEmpty())
    }

    @Test fun realActiveFirmwareLeaseBlocksDraftButInactiveDoesNot() {
        val activeSensor = ComponentRuntimeStatus(
            "sensor.soil_moisture", "sensor", "adc", "active", true, listOf(32)
        )
        assertFalse(PinAssignmentPolicy.validate("dht22", listOf(32),
            reported = listOf(activeSensor)).isEmpty())
        assertTrue(PinAssignmentPolicy.validate("dht22", listOf(32),
            reported = listOf(activeSensor.copy(active = false))).isEmpty())
        // RGB owns these pins at boot, whether or not its runtime lease is active.
        val inactiveRgb = ComponentRuntimeStatus(
            "led.rgb", "lighting", "pwm", "configured", false, listOf(27, 25, 26)
        )
        assertFalse(PinAssignmentPolicy.validate("dht22", listOf(27),
            reported = listOf(inactiveRgb)).isEmpty())
        assertTrue(PinAssignmentPolicy.validate("led_rgb", listOf(27, 25, 26),
            reported = listOf(inactiveRgb)).isEmpty())
    }

    @Test fun protectsReservedPinsAndUnknownProfiles() {
        assertFalse(PinAssignmentPolicy.validate("dht22", listOf(12)).isEmpty())
        assertFalse(PinAssignmentPolicy.validate("dht22", listOf(11)).isEmpty())
        assertFalse(PinAssignmentPolicy.validate("actuator_relay", listOf(21)).isEmpty()) // OLED SDA
        assertFalse(PinAssignmentPolicy.validate("actuator_relay", listOf(22)).isEmpty()) // OLED SCL
        assertFalse(PinAssignmentPolicy.validate("actuator_relay", listOf(23)).isEmpty()) // button
        assertFalse(PinAssignmentPolicy.validate("ghost", listOf(18)).isEmpty())
    }
}
