package com.rgbcontroller

import com.rgbcontroller.core.lab.RgbAutomationPolicy
import org.junit.Assert.*
import org.junit.Test

class RgbAutomationPolicyTest {
    @Test fun intervalsAndCyclesHaveHardCaps() {
        assertTrue(RgbAutomationPolicy.valid(2, 3))
        assertEquals(12, RgbAutomationPolicy.durationSeconds(2, 3))
        assertTrue(RgbAutomationPolicy.valid(60, 10))
        assertEquals(1200, RgbAutomationPolicy.durationSeconds(60, 10))
        for ((seconds, cycles) in listOf(0 to 1, 1 to 0, 61 to 1, 1 to 11,
            -1 to 3, 3600 to 999)) {
            assertFalse(RgbAutomationPolicy.valid(seconds, cycles))
        }
    }
}
