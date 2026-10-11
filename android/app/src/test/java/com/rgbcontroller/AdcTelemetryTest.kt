package com.rgbcontroller

import com.rgbcontroller.core.sensors.AdcSample
import com.rgbcontroller.core.sensors.AdcTelemetry
import org.junit.Assert.*
import org.junit.Test

class AdcTelemetryTest {
    @Test fun historyIsBoundedAndCsvIsDeterministic() {
        var samples = emptyList<AdcSample>()
        for (i in 0..150) samples = AdcTelemetry.append(samples, AdcSample(i * 500L, i))
        assertEquals(120, samples.size)
        assertEquals(31 * 500L, samples.first().elapsedMs)
        assertEquals(150, samples.last().raw)
        val csv = AdcTelemetry.csv(listOf(AdcSample(0, 10), AdcSample(500, 20)))
        assertEquals("elapsed_ms,adc1_raw\n0,10\n500,20", csv)
    }

    @Test fun rejectsInvalidAndBackwardsReadings() {
        assertThrows(IllegalArgumentException::class.java) {
            AdcTelemetry.append(emptyList(), AdcSample(-1, 0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AdcTelemetry.append(emptyList(), AdcSample(0, 4096))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AdcTelemetry.append(listOf(AdcSample(500, 3)), AdcSample(200, 4))
        }
    }
}
