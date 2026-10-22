package com.rgbcontroller

import com.rgbcontroller.core.components.alerts.AlertComparison
import com.rgbcontroller.core.components.alerts.AlertEngine
import com.rgbcontroller.core.components.alerts.AlertRule
import com.rgbcontroller.core.components.status.ComponentStatusRepository
import com.rgbcontroller.core.components.telemetry.TelemetryProvenance
import com.rgbcontroller.core.components.telemetry.TelemetrySample
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AlertEngineTest {

    private fun sample(
        componentId: String = "dht22",
        type: String = "temperature",
        value: Float = 40f,
        valid: Boolean = true,
        recordedAtMs: Long = 1000L
    ) = TelemetrySample(
        deviceId = "device-a", componentId = componentId, measurementType = type,
        value = value, unit = "C", status = if (valid) "fresh" else "invalid",
        valid = valid, sourceTimestampMs = recordedAtMs, recordedAtMs = recordedAtMs,
        provenance = TelemetryProvenance.FIRMWARE
    )

    private fun rule(
        threshold: Float = 35f,
        hysteresis: Float = 1f,
        minIntervalMs: Long = 60_000L,
        enabled: Boolean = true
    ) = AlertRule("r", "dht22", "temperature", AlertComparison.ABOVE, threshold,
        hysteresis = hysteresis, minIntervalMs = minIntervalMs, enabled = enabled)

    private fun latest(value: Float, valid: Boolean = true) =
        AlertEngine.latestBySeries(listOf(sample(value = value, valid = valid)))

    @Test fun raisesWhenAboveThreshold() {
        val evaluation = AlertEngine.evaluate(listOf(rule()), latest(40f), emptyMap(), 1000)
        assertEquals(1, evaluation.events.size)
        assertFalse(evaluation.events[0].clear)
        assertTrue(evaluation.states.getValue("r").active)
    }

    @Test fun neverRaisesTwiceWhileActive() {
        val first = AlertEngine.evaluate(listOf(rule()), latest(50f), emptyMap(), 1000)
        assertEquals(1, first.events.size)
        val second = AlertEngine.evaluate(listOf(rule()), latest(60f), first.states, 2000)
        assertTrue(second.events.isEmpty())
        assertTrue(second.states.getValue("r").active)
    }

    @Test fun hysteresisKeepsActiveInBandAndClearsWhenReleased() {
        val tripped = AlertEngine.evaluate(listOf(rule(hysteresis = 5f)), latest(40f), emptyMap(), 1000)
        // 32 is below the 35 threshold but inside the 5 degree band: stays active.
        val inBand = AlertEngine.evaluate(listOf(rule(hysteresis = 5f)), latest(32f), tripped.states, 1100)
        assertTrue(inBand.states.getValue("r").active)
        assertTrue(inBand.events.isEmpty())
        // 29 is below threshold - hysteresis: clears.
        val released = AlertEngine.evaluate(listOf(rule(hysteresis = 5f)), latest(29f), inBand.states, 1200)
        assertFalse(released.states.getValue("r").active)
        assertEquals(1, released.events.size)
        assertTrue(released.events[0].clear)
    }

    @Test fun rateLimitSuppressesReRaiseAfterClear() {
        val r = rule(minIntervalMs = 10_000L, hysteresis = 1f)
        val first = AlertEngine.evaluate(listOf(r), latest(50f), emptyMap(), 1000)
        val cleared = AlertEngine.evaluate(listOf(r), latest(10f), first.states, 2000)
        assertTrue(cleared.events.single().clear)
        // Re-trips 1s later, well within the 10s window: suppressed.
        val suppressed = AlertEngine.evaluate(listOf(r), latest(50f), cleared.states, 3000)
        assertTrue(suppressed.events.isEmpty())
        assertFalse(suppressed.states.getValue("r").active)
        // After the window, it raises again.
        val later = AlertEngine.evaluate(listOf(r), latest(50f), suppressed.states, 13_000)
        assertEquals(1, later.events.size)
        assertFalse(later.events[0].clear)
    }

    @Test fun invalidOrMissingReadingsNeverAlert() {
        assertTrue(AlertEngine.evaluate(listOf(rule()), emptyMap(), emptyMap(), 1000).events.isEmpty())
        assertTrue(AlertEngine.evaluate(listOf(rule()), latest(99f, valid = false), emptyMap(), 1000).events.isEmpty())
    }

    @Test fun disabledRuleIsIgnored() {
        val evaluation = AlertEngine.evaluate(listOf(rule(enabled = false)), latest(99f), emptyMap(), 1000)
        assertTrue(evaluation.events.isEmpty())
    }

    @Test fun repositoryEvaluatesAlertsOnIngest() = runBlocking {
        val storage = FakeComponentStatusStorage()
        val repo = ComponentStatusRepository(storage, clock = { 1000L })
        repo.setDevice("device-a")
        // The alert must be attributed to a firmware-confirmed physical model.
        repo.ingest("""{"version":1,"gpioAuthority":"firmware","components":[
            {"id":"sensor.dht22","type":"sensor","interface":"one_wire",
             "state":"active","active":true,"pins":[32]}]}""")
        repo.ingestTelemetry(
            """{"version":1,"measurements":[
               {"type":"temperature","status":"fresh","valid":true,"value":40.0,"unit":"C","timestampMs":1000}]}"""
        )
        val state = repo.state.value
        assertTrue(state.alertStates.getValue("dht22_temp_high").active)
        assertEquals(1, state.alertEvents.count { !it.clear })
    }

    @Test fun loadPersistedSeedsDefaultRulesOnce() = runBlocking {
        val storage = FakeComponentStatusStorage()
        val repo = ComponentStatusRepository(storage, clock = { 1000L })
        repo.loadPersisted()
        assertTrue(repo.state.value.alertRules.isNotEmpty())
        assertEquals(1, storage.saveAlertRulesCount)
    }
}
