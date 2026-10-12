package com.rgbcontroller

import com.rgbcontroller.core.model.RgbDeviceState
import com.rgbcontroller.core.wifi.CompiLabSession
import com.rgbcontroller.core.wifi.LabTransport
import org.junit.Assert.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

    @Test fun labNetworkOperationsNeverRunConcurrently() = runBlocking {
        val session = CompiLabSession()
        var active = 0
        var overlap = false
        coroutineScope {
            repeat(8) {
                launch {
                    session.serialized {
                        active++
                        if (active > 1) overlap = true
                        delay(5)
                        active--
                    }
                }
            }
        }
        assertFalse("HTTP calls must be serialized across modules", overlap)
        assertEquals(0, active)
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
