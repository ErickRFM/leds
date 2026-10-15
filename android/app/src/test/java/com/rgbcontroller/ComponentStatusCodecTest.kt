package com.rgbcontroller

import com.rgbcontroller.core.components.model.ComponentState
import com.rgbcontroller.core.components.model.ComponentStatusCodec
import org.junit.Assert.*
import org.junit.Test

class ComponentStatusCodecTest {

    private val validPayload = """
        {"version":1,"gpioAuthority":"firmware","components":[
          {"id":"led.rgb","type":"actuator","interface":"pwm","state":"active","active":true,"pins":[27,25,26]},
          {"id":"display.ssd1306","type":"display","interface":"i2c","state":"active","active":true,"pins":[21,22]}
        ]}
    """.trimIndent()

    @Test fun decodesAuthoritativeStatusWithResolvedPins() {
        val decoded = ComponentStatusCodec.decode(validPayload)
        assertNotNull(decoded)
        assertEquals(2, decoded!!.size)
        val rgb = ComponentStatusCodec.find(decoded, "led.rgb")!!
        assertTrue(rgb.active)
        assertEquals(listOf(27, 25, 26), rgb.pins)
        assertEquals(ComponentState.ACTIVE, ComponentStatusCodec.stateFor(rgb))
    }

    @Test fun rejectsStatusThatIsNotFirmwareAuthoritative() {
        assertNull(ComponentStatusCodec.decode(null))
        assertNull(ComponentStatusCodec.decode(""))
        assertNull(ComponentStatusCodec.decode("no soy json"))
        assertNull(ComponentStatusCodec.decode("{\"version\":2,\"gpioAuthority\":\"firmware\",\"components\":[]}"))
        assertNull(
            ComponentStatusCodec.decode(
                "{\"version\":1,\"gpioAuthority\":\"client\",\"components\":[]}"
            )
        )
        assertNull(ComponentStatusCodec.decode("{\"version\":1,\"gpioAuthority\":\"firmware\"}"))
    }

    @Test fun inactiveComponentNeverReportsActive() {
        val payload = """
            {"version":1,"gpioAuthority":"firmware","components":[
              {"id":"sensor.soil_moisture","type":"sensor","interface":"adc","state":"error","active":false,"pins":[34]}
            ]}
        """.trimIndent()
        val decoded = ComponentStatusCodec.decode(validPayload.run { this } + "")
        assertNotNull(decoded)
        val soil = ComponentStatusCodec.decode(
            "{\"version\":1,\"gpioAuthority\":\"firmware\",\"components\":[{\"id\":\"sensor.soil_moisture\",\"type\":\"sensor\",\"interface\":\"adc\",\"state\":\"error\",\"active\":false,\"pins\":[34]}]}"
        )!!
        val entry = ComponentStatusCodec.find(statuses = soil, id = "sensor.soil_moisture")!!
        assertFalse(entry.active)
        assertEquals(ComponentState.ERROR, ComponentStatusCodec.stateFor(entry))
        assertEquals(listOf(34), entry.pins)
    }

    @Test fun dropsInvalidPinsAndClampsEntryCount() {
        val entries = (0 until 20).joinToString(",") { i ->
            "{\"id\":\"c$i\",\"type\":\"sensor\",\"interface\":\"adc\",\"state\":\"active\",\"active\":true,\"pins\":[-1,99,34]}"
        }
        val decoded = ComponentStatusCodec.decode(
            "{\"version\":1,\"gpioAuthority\":\"firmware\",\"components\":[$entries]}"
        )!!
        assertEquals(ComponentStatusCodec.MAX_COMPONENTS, decoded.size)
        assertEquals(listOf(34), decoded.first().pins)
    }
}
