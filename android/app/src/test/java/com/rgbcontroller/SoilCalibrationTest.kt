package com.rgbcontroller

import com.rgbcontroller.core.sensors.SoilCalibration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoilCalibrationTest {
    @Test
    fun dryAndWetReferencesProduceBoundedPercentage() {
        val calibration = SoilCalibration(dryRaw = 40, wetRaw = 1200)
        assertEquals(0, calibration.percentage(40))
        assertEquals(100, calibration.percentage(1200))
        assertEquals(50, calibration.percentage(620))
        assertEquals(0, calibration.percentage(0))
        assertEquals(100, calibration.percentage(4095))
    }

    @Test
    fun reversedReferencesAreRejected() {
        val failures = runCatching { SoilCalibration(dryRaw = 4, wetRaw = 4) }
        assertTrue(failures.isFailure)
        val outOfRange = runCatching { SoilCalibration(dryRaw = -1, wetRaw = 100) }
        assertTrue(outOfRange.isFailure)
    }
}
