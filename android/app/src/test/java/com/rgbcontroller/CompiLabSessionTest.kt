package com.rgbcontroller

import com.rgbcontroller.core.model.RgbDeviceState
import com.rgbcontroller.core.wifi.CompiLabSession
import com.rgbcontroller.core.wifi.LabTransport
import org.junit.Assert.*
import org.junit.Test

class CompiLabSessionTest {
    private val red = RgbDeviceState(255, 0, 0, 255, true)

    @Test fun keysStayInMemoryAndInvalidateReadiness() {
        val session = CompiLabSession()
        session.selectTransport(LabTransport.WIFI_LAB)
        session.updateKey("1234567890abcdef")
        val (epoch, key) = session.beginVerification()
        assertTrue(session.verified(epoch, key, red))
        assertTrue(session.wifiReady)
        session.updateKey("abcdef1234567890")
        assertFalse(session.wifiReady)
        assertNull(session.rgbState)
        assertFalse(session.verified(epoch, key, red))
        assertEquals("abcdef1234567890", session.key)
        session.clear()
        assertEquals("", session.key)
        assertEquals(LabTransport.BLUETOOTH, session.transport)
    }

    @Test fun lateTransportResponsesCannotReconnectWifi() {
        val session = CompiLabSession()
        session.selectTransport(LabTransport.WIFI_LAB)
        session.updateKey("1234567890abcdef")
        val (epoch, key) = session.beginVerification()
        session.selectTransport(LabTransport.BLUETOOTH)
        assertFalse(session.verified(epoch, key, red))
        assertFalse(session.wifiReady)
        session.selectTransport(LabTransport.WIFI_LAB)
        val (next, newKey) = session.beginVerification()
        assertTrue(session.verified(next, newKey, red))
        assertFalse(session.commandReadback(epoch, key, red))
        session.failed(epoch, "obsolete failure")
        assertTrue(session.wifiReady)
        assertEquals(red, session.rgbState)
    }

    @Test fun cancelledOrFailedRequestNeverClaimsReady() {
        val session = CompiLabSession()
        session.selectTransport(LabTransport.WIFI_LAB)
        val (epoch, key) = session.beginVerification()
        session.failed(epoch, "connection failed")
        assertFalse(session.wifiReady)
        assertFalse(session.verified(epoch, key, red))
    }
}
