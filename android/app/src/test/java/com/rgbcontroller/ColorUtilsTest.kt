package com.rgbcontroller

import com.rgbcontroller.core.model.ColorUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorUtilsTest {

    @Test
    fun testRgbToHex() {
        val hex = ColorUtils.rgbToHex(255, 0, 128)
        assertEquals("#FF0080", hex)
    }

    @Test
    fun testSerializeSetCommand() {
        val cmd = ColorUtils.serializeSetCommand(255, 120, 0, 200)
        assertEquals("SET,255,120,0,200", cmd)
    }

    @Test
    fun testSerializeOffCommand() {
        val cmd = ColorUtils.serializeOffCommand()
        assertEquals("OFF", cmd)
    }

    @Test
    fun testSerializeOnCommand() {
        val cmd = ColorUtils.serializeOnCommand()
        assertEquals("ON", cmd)
    }
}
