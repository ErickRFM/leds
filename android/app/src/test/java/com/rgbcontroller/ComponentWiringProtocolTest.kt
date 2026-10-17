package com.rgbcontroller

import com.rgbcontroller.core.wifi.ComponentWiringProtocol
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComponentWiringProtocolTest {
    private val supported = JSONObject("""{"version":1,"revision":2,
        "persistence":"until_reboot","supported":["dht22","soil_moisture"]}""")
    private val receipt = JSONObject("""{"version":1,"componentId":"dht22",
        "requestId":"aabbccddee001122","outcome":"applied","pin":18,"revision":3,
        "persistence":"until_reboot"}""")
    private val confirmed = JSONObject("""{"version":1,"gpioAuthority":"firmware",
        "components":[{"id":"sensor.dht22","type":"sensor","state":"active",
        "active":true,"pins":[18]},{"id":"sensor.soil_moisture","state":"active",
        "active":true,"pins":[34]}]}""")

    @Test fun firmwareExplicitlyControlsMutableComponents() {
        assertTrue(ComponentWiringProtocol.supported(supported, "dht22"))
        assertTrue(ComponentWiringProtocol.supported(supported, "soil_moisture"))
        assertFalse(ComponentWiringProtocol.supported(supported, "led_rgb"))
        assertFalse(ComponentWiringProtocol.supported(supported, "ssd1306_oled"))
        assertFalse(ComponentWiringProtocol.supported(JSONObject("""{"version":2,
            "supported":["dht22"]}"""), "dht22"))
    }

    @Test fun receiptMustBindRequestAndVersionAndVolatility() {
        assertTrue(ComponentWiringProtocol.receiptMatches(
            receipt, "dht22", 18, "aabbccddee001122"))
        assertFalse(ComponentWiringProtocol.receiptMatches(
            receipt, "dht22", 19, "aabbccddee001122"))
        assertFalse(ComponentWiringProtocol.receiptMatches(
            receipt, "dht22", 18, "bbccddee001122aa"))
        assertFalse(ComponentWiringProtocol.receiptMatches(
            JSONObject("""{"version":1,"outcome":"applied","componentId":"dht22",
            "pin":18,"requestId":"aabbccddee001122","revision":3,
            "persistence":"durable"}"""), "dht22", 18, "aabbccddee001122"))
    }

    @Test fun readbackRequiresFirmwareAuthorityAndActiveLease() {
        assertTrue(ComponentWiringProtocol.confirmedByFirmware(confirmed, "dht22", 18))
        assertTrue(ComponentWiringProtocol.confirmedByFirmware(confirmed, "soil_moisture", 34))
        assertFalse(ComponentWiringProtocol.confirmedByFirmware(confirmed, "dht22", 32))
        assertFalse(ComponentWiringProtocol.confirmedByFirmware(confirmed, "led_rgb", 27))
        val forged = JSONObject("""{"version":1,"gpioAuthority":"local",
            "components":[{"id":"sensor.dht22","active":true,
            "state":"active","pins":[18]}]}""")
        assertFalse(ComponentWiringProtocol.confirmedByFirmware(forged, "dht22", 18))
        val inactive = JSONObject("""{"version":1,"gpioAuthority":"firmware",
            "components":[{"id":"sensor.dht22","active":false,
            "state":"configured","pins":[18]}]}""")
        assertFalse(ComponentWiringProtocol.confirmedByFirmware(inactive, "dht22", 18))
    }
}
