package com.rgbcontroller

import com.rgbcontroller.core.ble.BleLinkHealth
import com.rgbcontroller.core.ble.BleLinkQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The link header may only describe what the radio stack reported. These bounds
 * keep a missing RSSI from being rendered as a healthy link and keep the payload
 * arithmetic honest about the three ATT header bytes.
 */
class BleLinkHealthTest {
    private val base = BleLinkHealth(connected = true, mtu = 128, rssiDb = -70, measuredAtMs = 10_000L)

    @Test fun payloadLeavesRoomForTheAttHeader() {
        assertEquals(125, base.payloadBytes)
        assertEquals(20, base.copy(mtu = 23).payloadBytes)
        assertNull(base.copy(mtu = 3).payloadBytes)
        assertNull(base.copy(mtu = null).payloadBytes)
    }

    @Test fun qualityBandsAreOpenAtTheTop() {
        assertEquals(BleLinkQuality.STRONG, base.copy(rssiDb = -60).quality)
        assertEquals(BleLinkQuality.FAIR, base.copy(rssiDb = -61).quality)
        assertEquals(BleLinkQuality.FAIR, base.copy(rssiDb = -74).quality)
        assertEquals(BleLinkQuality.WEAK, base.copy(rssiDb = -75).quality)
        assertEquals(BleLinkQuality.CRITICAL, base.copy(rssiDb = -100).quality)
    }

    @Test fun unmeasuredLinksNeverClaimAQuality() {
        assertEquals(BleLinkQuality.UNKNOWN, base.copy(rssiDb = null).quality)
        assertEquals(BleLinkQuality.UNKNOWN, base.copy(connected = false).quality)
        assertEquals("Enlace sin medir", base.copy(rssiDb = null).qualityText())
    }

    @Test fun agesComeFromTheMeasurementStamp() {
        assertEquals(5, base.ageSeconds(15_000L))
        assertEquals("hace 5 s", base.ageText(15_000L))
        assertEquals("recién medida", base.ageText(10_000L))
        assertEquals("hace 1 s", base.ageText(11_000L))
        assertNull(base.ageSeconds(9_000L))
        assertNull(base.copy(connected = false).ageSeconds(15_000L))
    }

    @Test fun summaryReportsSignalAgeAndPayload() {
        val text = base.summary(15_000L)
        listOf("Enlace aceptable", "-70 dBm", "hace 5 s", "payload 125 B").forEach {
            assertTrue("$text no incluye $it", text.contains(it))
        }
    }

    @Test fun summaryWithoutAnMtuSaysNothingAboutPayload() {
        val text = base.copy(mtu = null).summary(12_000L)
        assertTrue(text.contains("-70 dBm"))
        assertTrue(!text.contains("payload"))
    }
}
