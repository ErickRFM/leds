package com.rgbcontroller

import com.rgbcontroller.core.components.model.ComponentMeasurement
import com.rgbcontroller.core.components.model.ComponentRuntimeStatus
import com.rgbcontroller.core.components.status.LastKnownStatus
import com.rgbcontroller.core.components.status.LastKnownStatusCodec
import com.rgbcontroller.core.components.status.LastKnownTelemetry
import com.rgbcontroller.core.components.status.LastKnownTelemetryCodec
import com.rgbcontroller.core.components.status.LocalDesiredComponent
import com.rgbcontroller.core.components.status.LocalDesiredConfig
import com.rgbcontroller.core.components.status.LocalDesiredConfigCodec
import org.junit.Assert.*
import org.junit.Test

class ComponentStatusPersistenceTest {

    private val components = listOf(
        ComponentRuntimeStatus("led.rgb", "actuator", "pwm", "active", true, listOf(27, 25, 26)),
        ComponentRuntimeStatus("display.ssd1306", "display", "i2c", "active", true, listOf(21, 22))
    )

    @Test fun lastKnownRoundTrip() {
        val status = LastKnownStatus("device-a", components, 1_700_000_000_000L)
        val decoded = LastKnownStatusCodec.decode(LastKnownStatusCodec.encode(status))!!
        assertEquals("device-a", decoded.deviceId)
        assertEquals(1_700_000_000_000L, decoded.confirmedAtMs)
        assertEquals(2, decoded.components.size)
        assertEquals(listOf(27, 25, 26), decoded.components[0].pins)
    }

    @Test fun rejectsMalformedOrUnknownSchemaWithoutCrashing() {
        assertNull(LastKnownStatusCodec.decode(null))
        assertNull(LastKnownStatusCodec.decode(""))
        assertNull(LastKnownStatusCodec.decode("no soy json"))
        assertNull(LastKnownStatusCodec.decode("{\"version\":2,\"deviceId\":\"x\",\"components\":[]}"))
        assertNull(LastKnownStatusCodec.decode("{\"version\":1,\"components\":[]}"))
    }

    @Test fun clampsUnboundedAndInvalidPinsWhenDecoding() {
        val raw = """
            {"version":1,"deviceId":"d","confirmedAtMs":5,"components":[
              {"id":"x","type":"sensor","interface":"adc","state":"active","active":true,"pins":[-1,99,34]}
            ]}
        """.trimIndent()
        val decoded = LastKnownStatusCodec.decode(raw)!!
        assertEquals(listOf(34), decoded.components.single().pins)
        assertEquals(5L, decoded.confirmedAtMs)
    }

    @Test fun lastKnownTelemetryRoundTrip() {
        val telemetry = LastKnownTelemetry(
            "device-a",
            listOf(
                ComponentMeasurement("humidity", "fresh", true, 48.3f, "%", 1200L),
                ComponentMeasurement("temperature", "fresh", true, 21.7f, "C", 1200L)
            ),
            1_700_000_000_000L
        )
        val decoded = LastKnownTelemetryCodec.decode(LastKnownTelemetryCodec.encode(telemetry))!!
        assertEquals("device-a", decoded.deviceId)
        assertEquals(1_700_000_000_000L, decoded.confirmedAtMs)
        assertEquals(2, decoded.measurements.size)
        assertEquals(48.3f, decoded.measurements[0].value, 0.001f)
    }

    @Test fun telemetryCodecRejectsMalformedOrUnknownSchema() {
        assertNull(LastKnownTelemetryCodec.decode(null))
        assertNull(LastKnownTelemetryCodec.decode(""))
        assertNull(LastKnownTelemetryCodec.decode("basura"))
        assertNull(LastKnownTelemetryCodec.decode("{\"version\":2,\"deviceId\":\"x\",\"measurements\":[]}"))
        assertNull(LastKnownTelemetryCodec.decode("{\"version\":1,\"measurements\":[]}"))
    }

    @Test fun desiredConfigRoundTrip() {
        val config = LocalDesiredConfig(
            listOf(
                LocalDesiredComponent("dht22", "sensor", listOf(32)),
                LocalDesiredComponent("led_rgb", "lighting", listOf(25, 26, 27))
            )
        )
        val decoded = LocalDesiredConfigCodec.decode(LocalDesiredConfigCodec.encode(config))
        assertEquals(2, decoded.components.size)
        assertEquals("dht22", decoded.components[0].id)
        assertEquals(listOf(25, 26, 27), decoded.components[1].pins)
    }

    @Test fun emptyDesiredConfigOnUnknownSchema() {
        assertEquals(LocalDesiredConfig.EMPTY, LocalDesiredConfigCodec.decode(null))
        assertEquals(LocalDesiredConfig.EMPTY, LocalDesiredConfigCodec.decode("{\"version\":9}"))
        assertEquals(LocalDesiredConfig.EMPTY, LocalDesiredConfigCodec.decode("basura"))
    }
}
