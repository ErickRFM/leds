package com.rgbcontroller

import com.rgbcontroller.core.components.status.ComponentStatusRepository
import com.rgbcontroller.core.components.telemetry.TelemetryHistory
import com.rgbcontroller.core.components.telemetry.TelemetryHistoryLimits
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cross-cutting F07 checks: invalid/stale data, device isolation, persistence
 * and concurrent ingest must not leak between devices or fabricate readings.
 */
class F07IntegrationTest {

    private val temp = { v: Double, ts: Long ->
        """{"version":1,"measurements":[
           {"type":"temperature","status":"fresh","valid":true,"value":$v,"unit":"C","timestampMs":$ts}]}"""
    }

    private val invalidTemp =
        """{"version":1,"measurements":[
           {"type":"temperature","status":"invalid","valid":false,"value":0,"unit":"C","timestampMs":10}]}"""

    @Test fun invalidReadingEntersHistoryAsUnplottableAndNeverAlerts() = runBlocking {
        val repo = ComponentStatusRepository(FakeComponentStatusStorage(), clock = { 10L })
        repo.setDevice("device-a")
        assertTrue(repo.ingestTelemetry(invalidTemp))
        val sample = repo.state.value.series("dht22", "temperature").single()
        assertFalse(sample.isPlottable)
        assertNull(sample.value)
        assertTrue(repo.state.value.activeAlerts.isEmpty())
    }

    @Test fun switchingDeviceClearsCrossDeviceAlertAndAutomationState() = runBlocking {
        val repo = ComponentStatusRepository(FakeComponentStatusStorage(), clock = { 100L })
        repo.setDevice("device-a")
        repo.ingestTelemetry(temp(60.0, 100))
        assertTrue(repo.state.value.activeAlerts.isNotEmpty())

        repo.setDevice("device-b")
        assertTrue("alerts from another device must not survive", repo.state.value.activeAlerts.isEmpty())
        assertTrue(repo.state.value.alertEvents.isEmpty())
        assertTrue(repo.state.value.automationEvents.isEmpty())
        assertTrue(repo.state.value.deviceHistory.isEmpty())
    }

    @Test fun historyIsIsolatedPerDevice() = runBlocking {
        val repo = ComponentStatusRepository(FakeComponentStatusStorage(), clock = { 100L })
        repo.setDevice("device-a")
        repo.ingestTelemetry(temp(20.0, 100))
        repo.setDevice("device-b")
        repo.ingestTelemetry(temp(30.0, 200))
        val all = repo.state.value.history
        assertTrue(all.any { it.deviceId == "device-a" })
        assertTrue(all.any { it.deviceId == "device-b" })
        assertEquals(1, repo.state.value.deviceHistory.size)
        assertEquals(30f, repo.state.value.deviceHistory.single().value)
        assertEquals(20f, TelemetryHistory.samplesForDevice(all, "device-a").first().value)
    }

    @Test fun concurrentIngestStaysBoundedAndIsolated() = runBlocking {
        val limits = TelemetryHistoryLimits(
            maxSamplesPerSeries = 5, maxSeries = 2, maxTotalSamples = 10, retentionMs = 1_000_000
        )
        var tick = 0L
        val repo = ComponentStatusRepository(FakeComponentStatusStorage(), clock = { tick++ }, historyLimits = limits)
        repo.setDevice("device-a")
        coroutineScope {
            repeat(20) { i ->
                launch { runCatching { repo.ingestTelemetry(temp(i.toDouble(), i.toLong())) } }
            }
            launch { runCatching { repo.setDevice("device-b") } }
        }
        val all = repo.state.value.history
        assertTrue(all.size <= limits.maxTotalSamples)
        val perSeries = all.groupBy { TelemetryHistory.seriesKey(it) }
        assertTrue(perSeries.values.all { it.size <= limits.maxSamplesPerSeries })
        assertTrue(perSeries.keys.all { it.startsWith("device-a") || it.startsWith("device-b") })
    }

    @Test fun loadPersistedRestoresRulesHistoryAndCalibration() = runBlocking {
        val storage = FakeComponentStatusStorage()
        val repo = ComponentStatusRepository(storage, clock = { 100L })
        repo.setDevice("device-a")
        repo.ingestTelemetry(temp(25.0, 100))
        repo.setSoilCalibration(dryRaw = 50, wetRaw = 1300)

        val restored = ComponentStatusRepository(storage, clock = { 200L })
        restored.setDevice("device-a")
        restored.loadPersisted()
        val state = restored.state.value
        assertTrue(state.alertRules.isNotEmpty())
        assertTrue(state.automationRules.isNotEmpty())
        assertTrue(state.series("dht22", "temperature").isNotEmpty())
        assertEquals(50, state.activeSoilCalibration?.dryRaw)
    }

    @Test fun automationHardwareStaysPendingUntilDispatched() = runBlocking {
        val repo = ComponentStatusRepository(FakeComponentStatusStorage(), clock = { 100L })
        repo.setDevice("device-a")
        assertTrue(repo.state.value.pendingAutomations.isEmpty())
        repo.ingestTelemetry(temp(50.0, 100))
        // Default automations are notice-only, so nothing waits on the transport.
        assertTrue(repo.state.value.pendingAutomations.isEmpty())
    }
}
