package com.rgbcontroller

import com.rgbcontroller.core.model.RgbDeviceState
import com.rgbcontroller.core.wifi.CompiLabSession
import com.rgbcontroller.core.wifi.LabTransport
import com.rgbcontroller.core.wifi.LabConnectionState
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
        assertEquals(LabConnectionState.CONTROL_KEY_REQUIRED, session.connectionState)
        session.updateKey("1234567890abcdef")
        assertEquals(LabConnectionState.LOCAL_ROUTE_AVAILABLE, session.connectionState)
        val (epoch, key) = session.beginVerification()
        assertEquals(LabConnectionState.VERIFYING, session.connectionState)
        assertTrue(session.verified(epoch, key, red))
        assertEquals(LabConnectionState.AUTHORIZED, session.connectionState)
        assertTrue(session.wifiReady)
        session.updateKey("abcdef1234567890")
        assertFalse(session.wifiReady)
        assertNull(session.rgbState)
        assertFalse(session.verified(epoch, key, red))
        assertEquals("abcdef1234567890", session.key)
        session.clear()
        assertEquals("", session.key)
        assertEquals(LabTransport.BLUETOOTH, session.transport)
        assertEquals(LabConnectionState.DISCONNECTED, session.connectionState)
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

    @Test fun userOperationShowsRealProgressAndFailure() = runBlocking {
        val session = CompiLabSession()
        assertEquals(com.rgbcontroller.core.wifi.LabOperationPhase.IDLE, session.operationStatus.phase)
        val applied = session.userOperation("GPIO19") {
            assertEquals(com.rgbcontroller.core.wifi.LabOperationPhase.RUNNING,
                session.operationStatus.phase)
            42
        }
        assertEquals(42, applied)
        assertEquals(com.rgbcontroller.core.wifi.LabOperationPhase.CONFIRMED,
            session.operationStatus.phase)
        try {
            session.userOperation("No conecta") {
                error("ESP32 no disponible")
            }
            fail("Expected simulated error")
        } catch (_: IllegalStateException) {
            assertEquals(com.rgbcontroller.core.wifi.LabOperationPhase.FAILED,
                session.operationStatus.phase)
            assertTrue(session.operationStatus.details.contains("ESP32"))
        }
    }

    @Test fun cancelledOrFailedRequestNeverClaimsReady() {
        val session = CompiLabSession()
        session.selectTransport(LabTransport.WIFI_LAB)
        val (epoch, key) = session.beginVerification()
        session.failed(epoch, "connection failed")
        assertFalse(session.wifiReady)
        assertFalse(session.verified(epoch, key, red))
        assertEquals(LabConnectionState.NETWORK_ERROR, session.connectionState)
    }

    @Test fun authFailureSetsCorrectConnectionState() {
        val session = CompiLabSession()
        session.selectTransport(LabTransport.WIFI_LAB)
        val (epoch, _) = session.beginVerification()
        session.failed(epoch, "ESP32 rechazó la operación: unauthorized")
        assertEquals(LabConnectionState.AUTH_FAILED, session.connectionState)
    }
}
