package com.rgbcontroller

import com.rgbcontroller.core.model.RgbDeviceCommand
import org.junit.Assert.*
import org.junit.Test

class RgbDeviceCommandTest {
    @Test fun fullRgbPaletteCanBeSentByEitherTransport() {
        val white = RgbDeviceCommand.set(255, 255, 255, 255)
        assertEquals("SET,255,255,255,255", white)
        assertTrue(RgbDeviceCommand.allowed(white))
        assertTrue(RgbDeviceCommand.allowed(RgbDeviceCommand.effect("BREATHE", 120)))
        assertTrue(RgbDeviceCommand.allowed(RgbDeviceCommand.effect("FADE", 1000)))
        assertTrue(RgbDeviceCommand.allowed("FX,STOP"))
        assertTrue(RgbDeviceCommand.allowed("ON"))
    }

    @Test fun invalidFramesDoNotReachTransport() {
        for (bad in listOf("", "set,1,2,3,4", "SET,256,0,0,0", "SET,-1,0,0,0",
            "SET,1,2,3", "SET,1,2,3,4,5", "FX,UNKNOWN,120", "FX,RAINBOW,9",
            "FX,FADE,1001", "FX,RAINBOW,100,extra", "GET extra")) {
            assertFalse("Accepted: " + bad, RgbDeviceCommand.allowed(bad))
        }
    }

    @Test fun builderRejectsOutOfRangeValues() {
        assertThrows(IllegalArgumentException::class.java) { RgbDeviceCommand.set(256, 0, 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { RgbDeviceCommand.effect("UNKNOWN", 200) }
        assertThrows(IllegalArgumentException::class.java) { RgbDeviceCommand.effect("RAINBOW", 0) }
    }

    @Test fun ownerKeyAuthorizationFrameIsSerializedButNeverTrustedHere() {
        assertEquals("AUTH 0123456789abcdef", RgbDeviceCommand.auth("0123456789abcdef"))
        assertTrue(RgbDeviceCommand.allowed("AUTH 0123456789abcdef"))
        assertNull(RgbDeviceCommand.auth(""))
        assertNull(RgbDeviceCommand.auth("   "))
        assertNull(RgbDeviceCommand.auth("bad key"))
        assertNull(RgbDeviceCommand.auth("a".repeat(33)))
        assertFalse(RgbDeviceCommand.allowed("AUTH "))
        assertFalse(RgbDeviceCommand.allowed("AUTH bad key"))
        assertFalse(RgbDeviceCommand.allowed("AUTH " + "a".repeat(33)))
    }
}
