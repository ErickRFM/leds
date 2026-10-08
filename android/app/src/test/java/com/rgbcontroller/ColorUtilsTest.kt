package com.rgbcontroller

import com.rgbcontroller.core.model.ColorUtils
import org.junit.Assert.*
import org.junit.Test

class ColorUtilsTest {
    @Test fun rgbToHex() {
        assertEquals("#FF0080", ColorUtils.rgbToHex(255, 0, 128))
        assertEquals("#FF0000", ColorUtils.rgbToHex(999, -20, 0))
    }

    @Test fun hsvPrimaryColors() {
        assertEquals(ColorUtils.RgbColor(255, 0, 0), ColorUtils.hsvToRgb(0f, 1f, 1f))
        assertEquals(ColorUtils.RgbColor(0, 255, 0), ColorUtils.hsvToRgb(120f, 1f, 1f))
        assertEquals(ColorUtils.RgbColor(0, 0, 255), ColorUtils.hsvToRgb(240f, 1f, 1f))
    }

    @Test fun commandSerialization() {
        assertEquals("SET,255,120,0,200", ColorUtils.serializeSetCommand(255, 120, 0, 200))
        assertEquals("OFF", ColorUtils.serializeOffCommand())
        assertEquals("ON", ColorUtils.serializeOnCommand())
        assertEquals("GET", ColorUtils.serializeGetCommand())
    }

    @Test fun hexParsing() {
        assertEquals(ColorUtils.RgbColor(255, 0, 128), ColorUtils.hexToRgb("#FF0080"))
        assertNull(ColorUtils.hexToRgb("#XYZ"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidCommand() {
        ColorUtils.serializeSetCommand(256, 0, 0, 255)
    }
}
