package com.rgbcontroller

import com.rgbcontroller.core.components.model.ComponentRuntimeStatus
import com.rgbcontroller.core.components.registry.ComponentCatalog
import com.rgbcontroller.core.components.status.OledRuntime
import org.junit.Assert.*
import org.junit.Test

class OledRuntimeTest {

    private fun status(
        state: String,
        active: Boolean,
        pins: List<Int> = listOf(21, 22)
    ) = ComponentRuntimeStatus(
        id = OledRuntime.FIRMWARE_ID,
        type = "display",
        interfaceType = "i2c",
        state = state,
        active = active,
        pins = pins
    )

    @Test fun absentModuleIsNeverReportedAsConfigured() {
        val oled = OledRuntime.from(null)
        assertFalse(oled.detected)
        assertFalse(oled.configured)
        assertFalse(oled.operative)
        assertEquals("absent", oled.state)
        assertNull(oled.sda)
        assertNull(oled.scl)
    }

    @Test fun activeModuleIsDetectedConfiguredAndOperative() {
        val oled = OledRuntime.from(status("active", active = true, pins = listOf(21, 22)))
        assertTrue(oled.detected)
        assertTrue(oled.configured)
        assertTrue(oled.operative)
        assertEquals(21, oled.sda)
        assertEquals(22, oled.scl)
    }

    @Test fun erroredModuleIsDetectedButNotConfiguredOrOperative() {
        val oled = OledRuntime.from(status("error", active = false, pins = emptyList()))
        assertTrue(oled.detected)
        assertFalse(oled.configured)
        assertFalse(oled.operative)
        assertEquals("error", oled.state)
        assertNull(oled.sda)
        assertNull(oled.scl)
    }

    @Test fun activeLeaseWithErrorStateIsNotOperative() {
        val oled = OledRuntime.from(status("error", active = true, pins = listOf(21, 22)))
        assertTrue(oled.detected)
        assertTrue(oled.configured)
        assertFalse("an error state must never be reported as an operative panel", oled.operative)
    }

    @Test fun defaultAddressAndCatalogMappingMatchFirmwareContract() {
        assertEquals(0x3C, OledRuntime.DEFAULT_ADDRESS)
        assertEquals("display.ssd1306", OledRuntime.FIRMWARE_ID)
        assertEquals("ssd1306_oled", OledRuntime.CATALOG_ID)
        assertEquals("ssd1306_oled", ComponentCatalog.catalogIdForFirmwareId("display.ssd1306"))
    }
}
