package com.rgbcontroller

import com.rgbcontroller.core.components.status.ComponentStatusFreshness
import com.rgbcontroller.core.components.status.ComponentStatusRepository
import com.rgbcontroller.feature.hardwarelab.ComponentDashboardViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ComponentDashboardViewModelTest {

    private val validPayload = """
        {"version":1,"gpioAuthority":"firmware","components":[
          {"id":"led.rgb","type":"actuator","interface":"pwm","state":"active","active":true,"pins":[27,25,26]}
        ]}
    """.trimIndent()

    private val validTelemetry = """
        {"version":1,"measurements":[
          {"type":"humidity","status":"fresh","valid":true,"value":48.30,"unit":"%","timestampMs":1200},
          {"type":"temperature","status":"fresh","valid":true,"value":21.70,"unit":"C","timestampMs":1200}
        ]}
    """.trimIndent()

    private class Harness {
        var authorized = true
        var device: String? = "device-a"
        var response: String? = null
        var failure: Exception? = null
        var telemetryResponse: String? = null
        var telemetryFailure: Exception? = null
        val storage = FakeComponentStatusStorage()
        val repo = ComponentStatusRepository(storage, clock = { 1000L })
        val viewModel = ComponentDashboardViewModel(
            repository = repo,
            isLinkAuthorized = { authorized },
            currentDeviceId = { device },
            fetchStatus = {
                failure?.let { throw it }
                response
            },
            fetchTelemetry = {
                telemetryFailure?.let { throw it }
                telemetryResponse
            }
        )
    }

    @Test fun authorizedFetchBecomesLive() = runBlocking {
        val h = Harness().apply { response = validPayload }
        h.viewModel.refresh()
        val state = h.viewModel.uiState.value
        assertEquals(ComponentStatusFreshness.LIVE, state.freshness)
        assertTrue(state.connected)
        assertEquals(1, state.authoritative?.size)
    }

    @Test fun unauthorizedNeverSimulatesConnection() = runBlocking {
        val h = Harness().apply { authorized = false; response = validPayload }
        h.viewModel.refresh()
        val state = h.viewModel.uiState.value
        assertFalse(state.connected)
        assertNull(state.firmwareStatus)
        assertEquals(ComponentStatusFreshness.NONE, state.freshness)
        assertNotNull(state.authorityError)
    }

    @Test fun transportFailureKeepsPreviousAuthoritativeState() = runBlocking {
        val h = Harness().apply { response = validPayload }
        h.viewModel.refresh()
        val before = h.viewModel.uiState.value.firmwareStatus
        assertNotNull(before)

        h.failure = IllegalStateException("timeout")
        h.viewModel.refresh()
        val after = h.viewModel.uiState.value
        assertEquals(before, after.firmwareStatus)
        assertNotNull(after.authorityError)
    }

    @Test fun invalidPayloadDoesNotFabricateLiveState() = runBlocking {
        val h = Harness().apply { response = "{\"version\":1,\"gpioAuthority\":\"client\"}" }
        h.viewModel.refresh()
        val state = h.viewModel.uiState.value
        assertNull(state.firmwareStatus)
        assertNotNull(state.authorityError)
    }

    @Test fun authorizedFetchIngestsRealTelemetry() = runBlocking {
        val h = Harness().apply { response = validPayload; telemetryResponse = validTelemetry }
        h.viewModel.refresh()
        val state = h.viewModel.uiState.value
        assertEquals(ComponentStatusFreshness.LIVE, state.telemetryFreshness)
        assertEquals(2, state.authoritativeTelemetry?.size)
        assertEquals(1, h.storage.saveTelemetryCount)
    }

    @Test fun telemetryFailureNeverDiscardsConfirmedStatus() = runBlocking {
        val h = Harness().apply { response = validPayload; telemetryResponse = validTelemetry }
        h.viewModel.refresh()
        val before = h.viewModel.uiState.value

        h.telemetryFailure = IllegalStateException("telemetry timeout")
        h.viewModel.refresh()
        val after = h.viewModel.uiState.value
        // Confirmed status and readings from the previous good fetch survive.
        assertEquals(before.firmwareStatus, after.firmwareStatus)
        assertEquals(before.firmwareTelemetry, after.firmwareTelemetry)
        assertNotNull(after.authorityError)
    }

    @Test fun connectionChangeTriggersRefreshWhenAuthorized() = runBlocking {
        val h = Harness().apply { response = validPayload }
        h.viewModel.onConnectionChanged()
        assertEquals(ComponentStatusFreshness.LIVE, h.viewModel.uiState.value.freshness)
    }

    @Test fun deviceChangeDropsStaleAuthoritativeData() = runBlocking {
        val h = Harness().apply { response = validPayload }
        h.viewModel.refresh()
        assertNotNull(h.viewModel.uiState.value.firmwareStatus)

        h.device = "device-b"
        h.failure = IllegalStateException("offline")
        h.viewModel.refresh()
        val state = h.viewModel.uiState.value
        assertNull(state.authoritative)
        assertNull(state.persisted)
        assertEquals(ComponentStatusFreshness.NONE, state.freshness)
    }
}
