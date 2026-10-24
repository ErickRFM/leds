package com.rgbcontroller

import com.rgbcontroller.core.components.telemetry.SampleQuality
import com.rgbcontroller.core.components.telemetry.TelemetryChart
import com.rgbcontroller.core.components.telemetry.TelemetryProvenance
import com.rgbcontroller.core.components.telemetry.TelemetrySample
import org.junit.Assert.*
import org.junit.Test

class TelemetryChartTest {

    private fun sample(
        type: String = "temperature",
        value: Float? = 20f,
        status: String = "fresh",
        valid: Boolean = true,
        recordedAtMs: Long
    ) = TelemetrySample(
        deviceId = "device-a",
        componentId = "dht22",
        measurementType = type,
        value = value,
        unit = "C",
        status = status,
        valid = valid,
        sourceTimestampMs = recordedAtMs,
        recordedAtMs = recordedAtMs,
        provenance = TelemetryProvenance.FIRMWARE
    )

    @Test fun validSamplesFormOneSegment() {
        val chart = TelemetryChart.build(
            "dht22", "temperature",
            listOf(sample(value = 10f, recordedAtMs = 1000), sample(value = 20f, recordedAtMs = 2000)),
            nowMs = 2000
        )
        assertEquals(1, chart.segments.size)
        assertEquals(2, chart.points.size)
        assertEquals(2, chart.validCount)
        assertFalse(chart.hasGap)
        // y normalised across the observed min..max
        assertEquals(0f, chart.points[0].fractionY, 0.001f)
        assertEquals(1f, chart.points[1].fractionY, 0.001f)
    }

    @Test fun invalidSampleInterruptsTheLine() {
        val chart = TelemetryChart.build(
            "dht22", "temperature",
            listOf(
                sample(value = 10f, recordedAtMs = 1000),
                sample(status = "sensor_error", valid = false, value = null, recordedAtMs = 2000),
                sample(value = 20f, recordedAtMs = 3000)
            ),
            nowMs = 3000
        )
        assertEquals(2, chart.segments.size)
        assertTrue(chart.hasGap)
        assertEquals(1, chart.invalidCount)
    }

    @Test fun staleStatusAndTooOldSamplesAreStaleNotPlotted() {
        val chart = TelemetryChart.build(
            "dht22", "temperature",
            listOf(
                sample(status = "stale", valid = false, value = null, recordedAtMs = 1000),
                sample(value = 20f, recordedAtMs = 2000)
            ),
            nowMs = 500_000,
            windowMs = 1_000_000,
            staleAfterMs = 50_000
        )
        // both are older than staleAfterMs -> STALE, none plotted
        assertEquals(0, chart.points.size)
        assertEquals(2, chart.staleCount)
        assertFalse(chart.hasData)
    }

    @Test fun outOfWindowSamplesAreIgnored() {
        val chart = TelemetryChart.build(
            "dht22", "temperature",
            listOf(sample(value = 5f, recordedAtMs = 1000), sample(value = 9f, recordedAtMs = 900_000)),
            nowMs = 1_000_000,
            windowMs = 60_000
        )
        assertEquals(0, chart.points.size)
    }

    @Test fun disconnectedIsPropagatedEvenWithHistory() {
        val chart = TelemetryChart.build(
            "dht22", "temperature",
            listOf(sample(value = 20f, recordedAtMs = 1000)),
            nowMs = 1000,
            disconnected = true
        )
        assertTrue(chart.disconnected)
        assertTrue(chart.hasData)
    }

    @Test fun qualityClassificationNeverUpgradesRawOrUncalibrated() {
        val now = 1000L
        assertEquals(
            SampleQuality.INVALID,
            TelemetryChart.classify(sample(status = "not_calibrated", valid = false, value = null, recordedAtMs = now), now, 100_000)
        )
        assertEquals(
            SampleQuality.VALID,
            TelemetryChart.classify(sample(recordedAtMs = now), now, 100_000)
        )
    }
    @Test fun shortLiveHistoryDoesNotCollapseAtRightEdge() {
        val now = 1_000_000L
        val chart = TelemetryChart.build(
            "dht11", "temperature",
            listOf(
                sample(value = 22.6f, recordedAtMs = now - 14_000),
                sample(value = 22.6f, recordedAtMs = now - 7_000),
                sample(value = 22.6f, recordedAtMs = now)
            ),
            nowMs = now,
            windowMs = 30 * 60 * 1000L
        )
        assertEquals(3, chart.points.size)
        assertTrue(chart.points[0].fractionX < 0.85f)
        assertTrue(chart.points[1].fractionX > chart.points[0].fractionX)
        assertEquals(1f, chart.points.last().fractionX, 0.001f)
        assertTrue(chart.points.all { it.fractionY == 0.5f })
    }

    @Test fun invalidSamplesRemainGapsWhileChartZooms() {
        val now = 1_000_000L
        val chart = TelemetryChart.build(
            "dht11", "humidity",
            listOf(
                sample(type = "humidity", value = 54.8f, recordedAtMs = now - 12_000),
                sample(type = "humidity", status = "sensor_error", value = null, valid = false, recordedAtMs = now - 6_000),
                sample(type = "humidity", value = 54.9f, recordedAtMs = now)
            ),
            nowMs = now
        )
        assertEquals(2, chart.segments.size)
        assertEquals(1, chart.invalidCount)
        assertEquals(2, chart.validCount)
    }

}
