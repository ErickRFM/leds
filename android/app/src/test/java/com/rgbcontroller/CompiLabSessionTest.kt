package com.rgbcontroller

import com.rgbcontroller.core.model.RgbDeviceState
import com.rgbcontroller.core.wifi.CompiLabSession
import com.rgbcontroller.core.wifi.LabHttpException
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

    @Test fun validKeyWithoutNetworkRouteDoesNotProduceLocalRouteAvailable() {
        val session = CompiLabSession()
        session.selectTransport(LabTransport.WIFI_LAB)
        assertFalse(session.isNetworkRouteAvailable)

        // Valid 16-char hex key supplied without network route
        session.updateKey("1234567890abcdef")
        assertNotEquals(LabConnectionState.LOCAL_ROUTE_AVAILABLE, session.connectionState)
        assertEquals(LabConnectionState.SOFTAP_DETECTED, session.connectionState)

        // Establish network route
        session.updateNetworkRouteAvailable(true)
        assertEquals(LabConnectionState.LOCAL_ROUTE_AVAILABLE, session.connectionState)
    }

    @Test fun keysStayInMemoryAndInvalidateReadiness() {
        val session = CompiLabSession()
        session.selectTransport(LabTransport.WIFI_LAB)
        session.updateNetworkRouteAvailable(true)
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
        session.updateNetworkRouteAvailable(true)
        session.updateKey("1234567890abcdef")
        val (epoch, key) = session.beginVerification()
        session.selectTransport(LabTransport.BLUETOOTH)
        assertFalse(session.verified(epoch, key, red))
        assertFalse(session.wifiReady)
        session.selectTransport(LabTransport.WIFI_LAB)
        session.updateNetworkRouteAvailable(true)
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
        session.updateNetworkRouteAvailable(true)
        val (epoch, key) = session.beginVerification()
        session.failed(epoch, "connection failed")
        assertFalse(session.wifiReady)
        assertFalse(session.verified(epoch, key, red))
        assertEquals(LabConnectionState.NETWORK_ERROR, session.connectionState)
    }

    @Test fun authFailureSetsCorrectConnectionState() {
        val session = CompiLabSession()
        session.selectTransport(LabTransport.WIFI_LAB)
        session.updateNetworkRouteAvailable(true)
        val (epoch, _) = session.beginVerification()
        session.failed(epoch, LabHttpException.Unauthorized(401, "ESP32 rechazó la operación: unauthorized"))
        assertEquals(LabConnectionState.AUTH_FAILED, session.connectionState)
    }

    @Test fun http409ConflictDoesNotInvalidateAuthorizedSession() {
        val session = CompiLabSession()
        session.selectTransport(LabTransport.WIFI_LAB)
        session.updateNetworkRouteAvailable(true)
        session.updateKey("1234567890abcdef")
        val (epoch, key) = session.beginVerification()
        assertTrue(session.verified(epoch, key, red))
        assertEquals(LabConnectionState.AUTHORIZED, session.connectionState)
        assertTrue(session.wifiReady)

        // Operational conflict (409) happens
        session.failed(epoch, LabHttpException.Conflict(409, "Operación simultánea en curso"))

        // Session must REMAIN authorized and wifiReady!
        assertEquals(LabConnectionState.AUTHORIZED, session.connectionState)
        assertTrue(session.wifiReady)
        assertEquals("Operación simultánea en curso", session.message)
    }

    @Test fun http401UnauthorizedInvalidatesSessionToAuthFailed() {
        val session = CompiLabSession()
        session.selectTransport(LabTransport.WIFI_LAB)
        session.updateNetworkRouteAvailable(true)
        session.updateKey("1234567890abcdef")
        val (epoch, key) = session.beginVerification()
        assertTrue(session.verified(epoch, key, red))

        // Unauthorized (401)
        session.failed(epoch, LabHttpException.Unauthorized(401, "Clave denegada"))
        assertEquals(LabConnectionState.AUTH_FAILED, session.connectionState)
        assertFalse(session.wifiReady)
    }

    @Test fun http400BadRequestDoesNotInvalidateAuthorizedSession() {
        val session = CompiLabSession()
        session.selectTransport(LabTransport.WIFI_LAB)
        session.updateNetworkRouteAvailable(true)
        session.updateKey("1234567890abcdef")
        val (epoch, key) = session.beginVerification()
        assertTrue(session.verified(epoch, key, red))

        // Bad request (400)
        session.failed(epoch, LabHttpException.BadRequest(400, "Parámetro inválido"))
        assertEquals(LabConnectionState.AUTHORIZED, session.connectionState)
        assertTrue(session.wifiReady)
        assertEquals("Parámetro inválido", session.message)
    }
    @Test fun routeLossRevokesWifiAndStaleReadbacksCannotReauthorize() {
        val session = CompiLabSession()
        session.selectTransport(LabTransport.WIFI_LAB)
        session.updateNetworkRouteAvailable(true)
        session.updateKey("1234567890abcdef")
        val (epoch, key) = session.beginVerification()
        assertTrue(session.verified(epoch, key, red))
        session.updateNetworkRouteAvailable(false)
        assertFalse(session.wifiReady)
        assertNull(session.rgbState)
        assertEquals(LabConnectionState.SOFTAP_DETECTED, session.connectionState)
        assertFalse(session.verified(epoch, key, red))
        assertFalse(session.commandReadback(epoch, key, red))
        session.updateNetworkRouteAvailable(true)
        assertEquals(LabConnectionState.LOCAL_ROUTE_AVAILABLE, session.connectionState)
        val (newEpoch, newKey) = session.beginVerification()
        assertTrue(session.verified(newEpoch, newKey, red))
    }

    @Test fun noRouteCannotBeAuthorizedEvenWithAResponse() {
        val session = CompiLabSession()
        session.selectTransport(LabTransport.WIFI_LAB)
        session.updateKey("1234567890abcdef")
        val (epoch, key) = session.beginVerification()
        assertFalse(session.verified(epoch, key, red))
        assertFalse(session.wifiReady)
    }

}
