package com.rgbcontroller

import com.rgbcontroller.core.components.model.ComponentTelemetryCodec
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

    private val calibratedSoilTelemetry = """
        {"version":1,"measurements":[
          {"type":"adc_raw","status":"fresh","valid":true,"value":2100,"unit":"raw","timestampMs":1300},
          {"type":"soil_moisture","status":"fresh","valid":true,"value":42.50,"unit":"%","timestampMs":1300}
        ]}
    """.trimIndent()

    private val uncalibratedSoilTelemetry = """
        {"version":1,"measurements":[
          {"type":"adc_raw","status":"fresh","valid":true,"value":2500,"unit":"raw","timestampMs":1350},
          {"type":"soil_moisture","status":"not_calibrated","valid":false,"value":0,"unit":"%","timestampMs":1350}
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

    @Test fun authorizedFetchIngestsCalibratedSoilTelemetry() = runBlocking {
        val h = Harness().apply { response = validPayload; telemetryResponse = calibratedSoilTelemetry }
        h.viewModel.refresh()
        val state = h.viewModel.uiState.value
        assertEquals(ComponentStatusFreshness.LIVE, state.telemetryFreshness)
        val moisture = state.authoritativeTelemetry?.let {
            ComponentTelemetryCodec.find(it, "soil_moisture")
        }
        assertNotNull(moisture)
        assertTrue(moisture!!.valid)
        assertEquals("fresh", moisture.status)
        assertEquals(42.5f, moisture.value, 0.01f)
        assertEquals(1, h.storage.saveTelemetryCount)
    }

    @Test fun uncalibratedSoilKeepsRawAdcButNoPercent() = runBlocking {
        val h = Harness().apply { response = validPayload; telemetryResponse = uncalibratedSoilTelemetry }
        h.viewModel.refresh()
        val decoded = h.viewModel.uiState.value.authoritativeTelemetry
        val moisture = decoded?.let {
            ComponentTelemetryCodec.find(it, "soil_moisture")
        }
        val raw = decoded?.let {
            ComponentTelemetryCodec.find(it, "adc_raw")
        }
        assertFalse("an uncalibrated reading must never be valid", moisture!!.valid)
        assertEquals("not_calibrated", moisture.status)
        assertTrue(raw!!.valid)
        assertEquals(2500f, raw.value, 0.01f)
    }

    @Test fun confirmedCatalogIdsMapFirmwareIdsToCatalogIds() = runBlocking {
        val h = Harness().apply { response = validPayload }
        h.viewModel.refresh()
        val known = h.viewModel.uiState.value.confirmedCatalogIds
        // The firmware reports `led.rgb`; the local desired config uses `led_rgb`.
        assertTrue("a confirmed component must be recognised by its catalog id", "led_rgb" in known)
        assertFalse("firmware ids must not leak into the catalog-id set", "led.rgb" in known)
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

    @Test fun editorSavedAlertRulesArePersistedAndExposed() = runBlocking {
        val h = Harness()
        val rule = com.rgbcontroller.core.components.alerts.AlertRule(
            "custom", "dht22", "temperature",
            com.rgbcontroller.core.components.alerts.AlertComparison.ABOVE, 40f,
            severity = com.rgbcontroller.core.components.alerts.AlertSeverity.CRITICAL
        )
        h.viewModel.setAlertRules(listOf(rule))
        assertEquals(listOf(rule), h.viewModel.uiState.value.alertRules)
        assertEquals(1, h.storage.saveAlertRulesCount)
        assertEquals(listOf(rule), h.storage.alertRules)
    }

    @Test fun calibrationSavedFromWizardBindsToCurrentDevice() = runBlocking {
        val h = Harness()
        val saved = h.viewModel.setSoilCalibration(dryRaw = 40, wetRaw = 1500)
        assertTrue(saved)
        assertEquals(1, h.storage.saveSoilCalibrationCount)
        assertEquals("device-a", h.storage.soilCalibration?.deviceId)
        assertNotNull(h.viewModel.uiState.value.soilCalibration)
    }

    @Test fun editorSavedAutomationRulesArePersistedAndExposed() = runBlocking {
        val h = Harness()
        val rule = com.rgbcontroller.core.components.automation.AutomationRule(
            "custom", "dht22", "temperature",
            com.rgbcontroller.core.components.alerts.AlertComparison.ABOVE, 45f,
            action = com.rgbcontroller.core.components.automation.AutomationAction
                .Notify("calor", com.rgbcontroller.core.components.alerts.AlertSeverity.WARNING),
            enabled = true
        )
        h.viewModel.setAutomationRules(listOf(rule))
        assertEquals(listOf(rule), h.viewModel.uiState.value.automationRules)
        assertEquals(1, h.storage.saveAutomationRulesCount)
        assertEquals(listOf(rule), h.storage.automationRules)
    }
}
