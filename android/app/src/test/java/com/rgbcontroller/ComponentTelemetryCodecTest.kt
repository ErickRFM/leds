package com.rgbcontroller

import com.rgbcontroller.core.components.model.ComponentMeasurement
import com.rgbcontroller.core.components.model.ComponentState
import com.rgbcontroller.core.components.model.ComponentTelemetryCodec
import com.rgbcontroller.core.components.registry.ComponentCatalog
import org.junit.Assert.*
import org.junit.Test

class ComponentTelemetryCodecTest {

    private val validPayload = """
        {"version":1,"measurements":[
          {"type":"humidity","status":"fresh","valid":true,"value":48.30,"unit":"%","timestampMs":1200},
          {"type":"temperature","status":"fresh","valid":true,"value":21.70,"unit":"C","timestampMs":1200}
        ]}
    """.trimIndent()

    @Test fun decodesValidTelemetry() {
        val decoded = ComponentTelemetryCodec.decode(validPayload)
        assertNotNull(decoded)
        assertEquals(2, decoded!!.size)
        val humidity = ComponentTelemetryCodec.find(decoded, "humidity")
        assertEquals("fresh", humidity!!.status)
        assertTrue(humidity.valid)
        assertEquals(48.3f, humidity.value, 0.001f)
        assertEquals(1200L, humidity.timestampMs)
    }

    @Test fun rejectsMalformedPayloadsWithoutFabricatingData() {
        assertNull(ComponentTelemetryCodec.decode(null))
        assertNull(ComponentTelemetryCodec.decode(""))
        assertNull(ComponentTelemetryCodec.decode("no soy json"))
        assertNull(ComponentTelemetryCodec.decode("{\"version\":2,\"measurements\":[]}"))
        assertNull(ComponentTelemetryCodec.decode("{\"version\":1}"))
    }

    @Test fun downgradesNonFiniteOrFlaggedInvalidValues() {
        val payload = """
            {"version":1,"measurements":[
              {"type":"humidity","status":"fresh","valid":true,"value":null},
              {"type":"temperature","status":"invalid","valid":false,"value":0,"unit":"C"}
            ]}
        """.trimIndent()
        val decoded = ComponentTelemetryCodec.decode(payload)!!
        val humidity = ComponentTelemetryCodec.find(decoded, "humidity")!!
        assertFalse("NaN must never be valid", humidity.valid)
        assertTrue(humidity.value.isNaN())
        val temperature = ComponentTelemetryCodec.find(decoded, "temperature")!!
        assertFalse(temperature.valid)
    }

    @Test fun boundsNumberOfMeasurements() {
        val entries = (1..40).joinToString(",") {
            "{\"type\":\"humidity\",\"status\":\"fresh\",\"valid\":true,\"value\":1.0,\"unit\":\"%\",\"timestampMs\":$it}"
        }
        val decoded = ComponentTelemetryCodec.decode("{\"version\":1,\"measurements\":[$entries]}")
        assertNotNull(decoded)
        assertEquals(ComponentTelemetryCodec.MAX_MEASUREMENTS, decoded!!.size)
    }

    @Test fun skipsUnknownStatusAndBlankType() {
        val payload = """
            {"version":1,"measurements":[
              {"type":"","status":"fresh","valid":true,"value":1.0,"unit":"","timestampMs":1},
              {"type":"humidity","status":"invented","valid":true,"value":1.0,"unit":"","timestampMs":1}
            ]}
        """.trimIndent()
        val decoded = ComponentTelemetryCodec.decode(payload)
        assertNotNull(decoded)
        assertTrue(decoded!!.isEmpty())
    }

    @Test fun mapsTelemetryToComponentState() {
        val decoded = ComponentTelemetryCodec.decode(validPayload)
        assertEquals(ComponentState.ACTIVE, ComponentTelemetryCodec.stateFor("dht22", decoded))
        // No telemetry for the soil component defaults to NO_DATA, never ACTIVE.
        assertEquals(ComponentState.NO_DATA, ComponentTelemetryCodec.stateFor("soil_moisture", decoded))
        // A null payload (decode failure) is NO_DATA, not a fake success.
        assertEquals(ComponentState.NO_DATA, ComponentTelemetryCodec.stateFor("dht22", null))
    }

    @Test fun notCalibratedSoilIsNoDataAndErrorsSurface() {
        val notCalibrated = ComponentTelemetryCodec.decode(
            """{"version":1,"measurements":[{"type":"soil_moisture","status":"not_calibrated","valid":false,"value":0,"unit":"%","timestampMs":1}]}"""
        )
        assertEquals(ComponentState.NO_DATA, ComponentTelemetryCodec.stateFor("soil_moisture", notCalibrated))

        val errored = ComponentTelemetryCodec.decode(
            """{"version":1,"measurements":[{"type":"humidity","status":"sensor_error","valid":false,"value":0,"unit":"%","timestampMs":1}]}"""
        )
        assertEquals(ComponentState.ERROR, ComponentTelemetryCodec.stateFor("dht22", errored))
    }

    @Test fun encodeThenDecodeRoundTripsRealReadings() {
        val measurements = listOf(
            ComponentMeasurement("humidity", "fresh", true, 48.3f, "%", 1200L),
            ComponentMeasurement("temperature", "fresh", true, 21.7f, "C", 1200L)
        )
        val decoded = ComponentTelemetryCodec.decode(
            ComponentTelemetryCodec.encode(measurements).toString()
        )!!
        assertEquals(2, decoded.size)
        val humidity = ComponentTelemetryCodec.find(decoded, "humidity")!!
        assertTrue(humidity.valid)
        assertEquals(48.3f, humidity.value, 0.001f)
        assertEquals(1200L, humidity.timestampMs)
    }

    @Test fun encodeNeverPersistsNonFiniteValueAsANumber() {
        val encoded = ComponentTelemetryCodec.encode(
            listOf(ComponentMeasurement("humidity", "fresh", true, Float.NaN, "%", 1L))
        ).toString()
        // NaN is stored as JSON null and must decode back to an invalid measurement.
        assertFalse(encoded.contains("NaN"))
        val decoded = ComponentTelemetryCodec.decode(encoded)!!
        assertFalse(decoded.single().valid)
        assertTrue(decoded.single().value.isNaN())
    }

    @Test fun catalogIdsMatchFirmwareContract() {
        val ids = ComponentCatalog.components.map { it.id }.toSet()
        assertTrue(ids.containsAll(setOf("dht22", "soil_moisture", "ldr_light", "led_rgb", "ssd1306_oled")))
        assertEquals("dht22", ComponentCatalog.get("dht22")?.id)
        assertEquals("ldr_light", ComponentCatalog.get("ldr_light")?.id)
        assertNull(ComponentCatalog.get("does_not_exist"))
    }

    @Test fun catalogIdForFirmwareIdResolvesFirmwarePrefixes() {
        assertEquals("dht22", ComponentCatalog.catalogIdForFirmwareId("sensor.dht22"))
        assertEquals("soil_moisture", ComponentCatalog.catalogIdForFirmwareId("sensor.soil_moisture"))
        assertEquals("ldr_light", ComponentCatalog.catalogIdForFirmwareId("sensor.ldr_light"))
        assertEquals("led_rgb", ComponentCatalog.catalogIdForFirmwareId("led.rgb"))
        assertEquals("ssd1306_oled", ComponentCatalog.catalogIdForFirmwareId("display.ssd1306"))
        // An unknown module is returned unchanged so it is never silently hidden.
        assertEquals("future.thing", ComponentCatalog.catalogIdForFirmwareId("future.thing"))
    }

    @Test fun rawAdcAloneMakesSoilActiveWithoutACalibratedPercent() {
        val decoded = ComponentTelemetryCodec.decode(
            """{"version":1,"measurements":[{"type":"adc_raw","status":"fresh","valid":true,"value":2500,"unit":"raw","timestampMs":1}]}"""
        )
        assertEquals(ComponentState.ACTIVE, ComponentTelemetryCodec.stateFor("soil_moisture", decoded))
        // Soil ADC must never impersonate the LDR ADC channel.
        assertEquals(ComponentState.NO_DATA, ComponentTelemetryCodec.stateFor("ldr_light", decoded))
        val ldr = ComponentTelemetryCodec.decode(
            """{"version":1,"measurements":[
                {"type":"ambient_light_raw","status":"fresh","valid":true,"value":1800,"unit":"raw","timestampMs":2},
                {"type":"ambient_light","status":"not_calibrated","valid":false,"value":0,"unit":"%","timestampMs":2}
            ]}"""
        )
        assertEquals(ComponentState.ACTIVE, ComponentTelemetryCodec.stateFor("ldr_light", ldr))
        assertEquals(ComponentState.NO_DATA, ComponentTelemetryCodec.stateFor("soil_moisture", ldr))
    }
}
