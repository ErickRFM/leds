package com.rgbcontroller

import com.rgbcontroller.core.hardware.Esp32BoardPinout
import com.rgbcontroller.core.hardware.PinKind
import org.junit.Assert.*
import org.junit.Test

class Esp32BoardPinoutTest {
    @Test fun rgbAndSystemPnsCannotBeControlledAsGeneralGpio() {
        for (pin in listOf(0, 6, 7, 8, 9, 10, 11, 12, 25, 26, 27, 34, 35, 36, 39)) {
            assertFalse("GPIO " + pin, Esp32BoardPinout.canWrite(pin))
        }
        assertEquals(setOf(18, 19, 21, 22, 23), Esp32BoardPinout.manualOutputs)
        assertTrue(Esp32BoardPinout.pins.any { it.number == 27 && it.kind == PinKind.RGB_RESERVED })
    }

    @Test fun adc1IsReadOnlyOrStrictlyOptedIn() {
        assertTrue(Esp32BoardPinout.canReadAdc1(34))
        assertTrue(Esp32BoardPinout.canReadAdc1(32))
        assertFalse(Esp32BoardPinout.canReadAdc1(25))
        assertFalse(Esp32BoardPinout.canRead(6))
        assertFalse(Esp32BoardPinout.canRead(27))
        assertTrue(Esp32BoardPinout.canRead(18))
    }
}
