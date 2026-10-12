package com.rgbcontroller

import com.rgbcontroller.core.model.BleGpioSnapshot
import org.junit.Assert.*
import org.junit.Test

class BleGpioSnapshotTest {
    @Test fun acceptsOnlyBoundedCanonicalPinFrames() {
        val original = "PINS,18:F,19:H,21:F,22:F,23:E,25:R,26:R,27:R"
        val parsed = BleGpioSnapshot.parse(original)
        assertNotNull(parsed)
        assertEquals(8, parsed!!.size)
        assertEquals("Salida manual HIGH", parsed[1].description)
        assertEquals("Reservado por RGB", parsed.last().description)
        assertNull(BleGpioSnapshot.parse(original.replace("19:H", "19:Z")))
        assertNull(BleGpioSnapshot.parse(original.replace("25:R", "25:Q")))
        assertNull(BleGpioSnapshot.parse(original.replace("19:H", "20:H")))
        assertNull(BleGpioSnapshot.parse(original.replace("PINS", "KEYS")))
        assertNull(BleGpioSnapshot.parse(original + ",0:F"))
    }
}
