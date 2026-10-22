package com.rgbcontroller

import com.rgbcontroller.core.audio.EnvironmentalSpeechFormatter
import com.rgbcontroller.core.components.model.ComponentMeasurement
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentalSpeechFormatterTest {
    private fun measurement(
        type: String, value: Float, valid: Boolean = true,
        status: String = "fresh"
    ) = ComponentMeasurement(type, status, valid, value, "%", 1000L)

    @Test fun speaksOnlyLiveValuesFromCurrentComponent() {
        val report = EnvironmentalSpeechFormatter.report(
            listOf(
                measurement("temperature", 22.3f),
                measurement("humidity", 60f),
                measurement("ambient_light", 35f)
            ), includeDht = true, includeLight = true
        )
        assertTrue(report.hasFreshData)
        // es-MX may use '.' while other Spanish locales use ',' as decimal separator.
        val normalized = report.text.replace(',', '.')
        assertTrue(normalized.contains("22.3"))
        assertTrue(normalized.contains("60.0"))
        assertTrue(normalized.contains("35.0"))
    }

    @Test fun rejectsStaleInvalidAndUncalibratedValues() {
        val report = EnvironmentalSpeechFormatter.report(
            listOf(
                measurement("temperature", 70f, status = "stale"),
                measurement("humidity", 47f, valid = false),
                measurement("ambient_light", 0f, valid = false, status = "not_calibrated"),
                measurement("ambient_light_raw", 2300f)
            ), includeDht = true, includeLight = true
        )
        assertFalse(report.hasFreshData)
        assertTrue(report.text.contains("No hay mediciones"))
    }

    @Test fun neverAnnouncesSensorNotActiveEvenWithCachedValues() {
        val report = EnvironmentalSpeechFormatter.report(
            listOf(measurement("temperature", 27f), measurement("ambient_light", 48f)),
            includeDht = false, includeLight = false
        )
        assertFalse(report.hasFreshData)
    }

    @Test fun refusesImpossibleNumbers() {
        val report = EnvironmentalSpeechFormatter.report(
            listOf(
                measurement("temperature", Float.NaN),
                measurement("humidity", 140f),
                measurement("ambient_light", -1f)
            ), includeDht = true, includeLight = true
        )
        assertFalse(report.hasFreshData)
    }
}
