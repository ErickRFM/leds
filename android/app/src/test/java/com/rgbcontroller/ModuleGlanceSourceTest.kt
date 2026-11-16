package com.rgbcontroller

import com.rgbcontroller.core.components.model.ComponentMeasurement
import com.rgbcontroller.core.components.status.ComponentDashboardState
import com.rgbcontroller.core.components.status.FirmwareReportedTelemetry
import com.rgbcontroller.core.components.status.LastKnownTelemetry
import com.rgbcontroller.feature.modules.ModuleGlanceSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The list glance is the only reading a user sees without opening a module, so
 * it may show a number only when the firmware actually measured one. No Compose
 * and no device: these are the honesty rules themselves.
 */
class ModuleGlanceSourceTest {
    private fun measurement(
        type: String,
        value: Float,
        valid: Boolean = true,
        status: String = "fresh"
    ) = ComponentMeasurement(type, status, valid, value, "%", 1234L)

    private fun live(vararg measurements: ComponentMeasurement) = ComponentDashboardState(
        deviceId = "esp32-a",
        connected = true,
        firmwareTelemetry = FirmwareReportedTelemetry("esp32-a", measurements.toList(), 1234L)
    )

    private fun saved(vararg measurements: ComponentMeasurement) = ComponentDashboardState(
        deviceId = "esp32-a",
        connected = false,
        lastKnownTelemetry = LastKnownTelemetry("esp32-a", measurements.toList(), 1234L)
    )

    @Test fun liveDhtShowsItsRealTemperatureAndHumidity() {
        val glance = ModuleGlanceSource.forModule(
            "dht11", live(measurement("temperature", 21.74f), measurement("humidity", 48.3f))
        )!!
        assertEquals("21.7", glance.value)
        assertEquals("°C", glance.unit)
        assertEquals("Humedad 48 %", glance.detail)
        assertTrue(glance.live)
        assertEquals("● En vivo", glance.provenance)
    }

    @Test fun staleOrInvalidMeasurementsNeverBecomeAGlanceNumber() {
        val glance = ModuleGlanceSource.forModule(
            "dht22",
            live(
                measurement("temperature", Float.NaN, valid = false),
                measurement("humidity", 99f, status = "stale")
            )
        )!!
        assertEquals("—", glance.value)
        assertEquals("Sin lecturas válidas", glance.detail)
        assertFalse(glance.detail.any { it.isDigit() })
    }

    @Test fun lastKnownValuesAreClearlyNotLive() {
        val glance = ModuleGlanceSource.forModule("dht11", saved(measurement("temperature", 19.2f)))!!
        assertEquals("19.2", glance.value)
        assertFalse(glance.live)
        assertEquals("No en vivo", glance.provenance)
    }

    @Test fun nothingConfirmedShowsNoDigitsAtAll() {
        val glance = ModuleGlanceSource.forModule("soil_moisture", ComponentDashboardState(deviceId = "a"))!!
        assertEquals("—", glance.value)
        assertEquals("Sin confirmación del firmware", glance.detail)
        assertFalse((glance.value + glance.detail).any { it.isDigit() })
    }

    @Test fun uncalibratedSoilShowsTheRawAdcItActuallyHas() {
        val glance = ModuleGlanceSource.forModule(
            "soil_moisture", live(measurement("adc_raw", 3120f))
        )!!
        assertEquals("ADC del suelo · sin calibrar", glance.label)
        assertEquals("3120", glance.value)
        assertEquals("/ 4095", glance.unit)
        assertEquals("Calibra para obtener porcentaje", glance.detail)
    }

    @Test fun calibratedLdrReportsPercentAndKeepsRawAsDetail() {
        val glance = ModuleGlanceSource.forModule(
            "ldr_light",
            live(measurement("ambient_light", 62.4f), measurement("ambient_light_raw", 2800f))
        )!!
        assertEquals("62", glance.value)
        assertEquals("%", glance.unit)
        assertEquals("ADC 2800 / 4095", glance.detail)
    }

    @Test fun environmentalGlanceNamesTheModelTheScreenResolved() {
        val glance = ModuleGlanceSource.forModule(
            "environmental_workspace",
            live(
                measurement("temperature", 21.7f),
                measurement("humidity", 48.3f),
                measurement("soil_moisture", 61.2f)
            ),
            climateModel = "dht22"
        )!!
        assertEquals("DHT22 · Temperatura", glance.label)
        assertEquals("21.7", glance.value)
        assertTrue(glance.detail.contains("Humedad 48 %"))
        assertTrue(glance.detail.contains("Suelo 61 %"))
    }

    @Test fun modulesWithoutARealReadingHaveAGlance() {
        assertNull(ModuleGlanceSource.forModule("led_rgb", live(measurement("temperature", 21f))))
        assertNull(ModuleGlanceSource.forModule("robotics_crane", live()))
        assertNull(ModuleGlanceSource.forModule("ssd1306_oled", ComponentDashboardState()))
    }

    @Test fun wrongDeviceIdCannotLeakAnotherBoardsReadings() {
        val state = ComponentDashboardState(
            deviceId = "esp32-b",
            connected = true,
            firmwareTelemetry = FirmwareReportedTelemetry(
                "esp32-a", listOf(measurement("temperature", 30f)), 1234L
            )
        )
        val glance = ModuleGlanceSource.forModule("dht11", state)!!
        assertEquals("—", glance.value)
        assertFalse(glance.live)
    }
}
