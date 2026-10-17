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
        val runtime = ComponentRuntimeStatus(
            "led.rgb", "lighting", "pwm", "active", true, listOf(27, 25, 26)
        )
        assertFalse(PinAssignmentPolicy.validate("dht22", listOf(27),
            reported = listOf(runtime)).isEmpty())
        assertTrue(PinAssignmentPolicy.validate("led_rgb", listOf(27, 25, 26),
            reported = listOf(runtime)).isEmpty())
        assertTrue(PinAssignmentPolicy.validate("dht22", listOf(27),
            reported = listOf(runtime.copy(active = false))).isEmpty())
    }

    @Test fun protectsReservedPinsAndUnknownProfiles() {
        assertFalse(PinAssignmentPolicy.validate("dht22", listOf(12)).isEmpty())
        assertFalse(PinAssignmentPolicy.validate("dht22", listOf(11)).isEmpty())
        assertFalse(PinAssignmentPolicy.validate("ghost", listOf(18)).isEmpty())
    }
}
