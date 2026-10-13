package com.rgbcontroller

import com.rgbcontroller.core.sensors.SoilCalibration
import org.junit.Assert.*
import org.junit.Test

class SoilCalibrationTest {
    @Test fun acceptsDescendingAdcAndClamps() {
        val calibration = SoilCalibration(dryRaw = 3200, wetRaw = 1200)
        assertEquals(0, calibration.percentage(3200))
        assertEquals(50, calibration.percentage(2200))
        assertEquals(100, calibration.percentage(1200))
        assertEquals(0, calibration.percentage(4095))
        assertEquals(100, calibration.percentage(0))
    }

    @Test fun acceptsAscendingAdc() {
        val calibration = SoilCalibration(dryRaw = 500, wetRaw = 3500)
        assertEquals(50, calibration.percentage(2000))
    }

    @Test fun rejectsNoiseAndInvalidAdcValues() {
        assertThrows(IllegalArgumentException::class.java) { SoilCalibration(1500, 1550) }
        assertThrows(IllegalArgumentException::class.java) { SoilCalibration(4096, 100) }
        assertThrows(IllegalArgumentException::class.java) { SoilCalibration(100, 1000).percentage(-1) }
    }
}
