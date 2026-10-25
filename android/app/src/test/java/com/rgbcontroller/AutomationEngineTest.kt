package com.rgbcontroller

import com.rgbcontroller.core.components.alerts.AlertComparison
import com.rgbcontroller.core.components.automation.AutomationAction
import com.rgbcontroller.core.components.automation.AutomationActionSink
import com.rgbcontroller.core.components.automation.AutomationCoordinator
import com.rgbcontroller.core.components.automation.AutomationEngine
import com.rgbcontroller.core.components.automation.AutomationEvent
import com.rgbcontroller.core.components.automation.AutomationEventStatus
import com.rgbcontroller.core.components.automation.AutomationRule
import com.rgbcontroller.core.components.automation.AutomationRulesCodec
import com.rgbcontroller.core.components.automation.DefaultAutomationRules
import com.rgbcontroller.core.components.alerts.AlertEngine
import com.rgbcontroller.core.components.status.ComponentStatusRepository
import com.rgbcontroller.core.components.telemetry.TelemetryProvenance
import com.rgbcontroller.core.components.telemetry.TelemetrySample
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationEngineTest {

    private fun sample(
        value: Float = 40f,
        valid: Boolean = true,
        recordedAtMs: Long = 1000L
    ) = TelemetrySample(
        deviceId = "device-a", componentId = "dht22", measurementType = "temperature",
        value = value, unit = "C", status = if (valid) "fresh" else "invalid",
        valid = valid, sourceTimestampMs = recordedAtMs, recordedAtMs = recordedAtMs,
        provenance = TelemetryProvenance.FIRMWARE
    )

    private fun latest(value: Float, valid: Boolean = true) =
        AlertEngine.latestBySeries(listOf(sample(value, valid, 1000L)))

    private fun notifyRule(enabled: Boolean = true) = AutomationRule(
        "auto_notice", "dht22", "temperature", AlertComparison.ABOVE, 35f, hysteresis = 1f,
        action = AutomationAction.Notify("calor"), enabled = enabled
    )

    private fun rgbRule(enabled: Boolean = true) = AutomationRule(
        "auto_rgb", "dht22", "temperature", AlertComparison.ABOVE, 35f, hysteresis = 1f,
        action = AutomationAction.SetRgb(255, 0, 0, 100), enabled = enabled
    )

    @Test fun defaultTemperatureNoticeIncludesConnectedDht11() {
        val rules = DefaultAutomationRules.defaults()
        assertTrue(rules.any { it.componentId == "dht11" &&
            it.measurementType == "temperature" && it.enabled })
        assertTrue(rules.any { it.componentId == "dht22" &&
            it.measurementType == "temperature" && it.enabled })
    }

    @Test fun notifyRuleIsRecordedInline() {
        val outcome = AutomationEngine.evaluate(listOf(notifyRule()), latest(40f), emptyMap(), 1000L)
        assertEquals(1, outcome.events.size)
        assertEquals(AutomationEventStatus.NOTIFIED, outcome.events.single().status)
    }

    @Test fun hardwareRuleIsLeftPending() {
        val outcome = AutomationEngine.evaluate(listOf(rgbRule()), latest(40f), emptyMap(), 1000L)
        val event = outcome.events.single()
        assertEquals(AutomationEventStatus.PENDING, event.status)
        assertTrue(rgbRule().isHardware)
    }

    @Test fun invalidOrMissingReadingsNeverTrigger() {
        assertTrue(AutomationEngine.evaluate(listOf(rgbRule()), emptyMap(), emptyMap(), 1000L).events.isEmpty())
        assertTrue(AutomationEngine.evaluate(listOf(rgbRule()), latest(99f, valid = false), emptyMap(), 1000L).events.isEmpty())
    }

    @Test fun hardwareRuleDoesNotRepeatWhileActive() {
        val first = AutomationEngine.evaluate(listOf(rgbRule()), latest(50f), emptyMap(), 1000L)
        assertEquals(1, first.events.size)
        val again = AutomationEngine.evaluate(listOf(rgbRule()), latest(50f), first.states, 1100L)
        assertTrue(again.events.isEmpty())
    }

    @Test fun disabledRuleIsIgnored() {
        assertTrue(AutomationEngine.evaluate(listOf(rgbRule(enabled = false)), latest(99f), emptyMap(), 1000L).events.isEmpty())
    }

    @Test fun codecRoundTripsAllActionTypes() {
        val rules = listOf(
            AutomationRule("a", "dht22", "temperature", AlertComparison.ABOVE, 45f,
                action = AutomationAction.Notify("hola"), enabled = true),
            rgbRule(),
            AutomationRule(
                "auto_oled", "soil_moisture", "soil_moisture", AlertComparison.BELOW, 20f,
                action = AutomationAction.UpdateOled("seco")
            )
        )
        assertEquals(rules, AutomationRulesCodec.decode(AutomationRulesCodec.encode(rules)))
        assertTrue(AutomationRulesCodec.decode(null).isEmpty())
        assertTrue(AutomationRulesCodec.decode("{\"version\":99}").isEmpty())
    }

    @Test fun coordinatorBlocksHardwareWithoutAuthorization() = runBlocking {
        val pending = listOf(event(rgbRule()))
        val blocked = AutomationCoordinator.dispatch(pending, authorized = false, sink = AutomationActionSink { true })
        assertEquals(AutomationEventStatus.BLOCKED, blocked.single().status)
    }

    @Test fun coordinatorDispatchesHardwareThroughSinkWhenAuthorized() = runBlocking {
        var seen: AutomationAction? = null
        val sink = AutomationActionSink { action -> seen = action; true }
        val result = AutomationCoordinator.dispatch(listOf(event(rgbRule())), authorized = true, sink = sink)
        assertEquals(AutomationEventStatus.DISPATCHED, result.single().status)
        assertTrue(seen is AutomationAction.SetRgb)
    }

    @Test fun coordinatorMarksFailureWhenSinkFails() = runBlocking {
        val failing = AutomationCoordinator.dispatch(
            listOf(event(rgbRule())), authorized = true,
            sink = AutomationActionSink { throw IllegalStateException("boom") }
        )
        assertEquals(AutomationEventStatus.FAILED, failing.single().status)
        val refused = AutomationCoordinator.dispatch(
            listOf(event(rgbRule())), authorized = true, sink = AutomationActionSink { false }
        )
        assertEquals(AutomationEventStatus.FAILED, refused.single().status)
    }

    @Test fun repositoryRunsPendingAutomationsOnlyWhenAuthorized() = runBlocking {
        val storage = FakeComponentStatusStorage()
        val repo = ComponentStatusRepository(storage, clock = { 1000L })
        repo.setDevice("device-a")
        repo.setAutomationRules(listOf(rgbRule()))
        repo.ingest("""{"version":1,"gpioAuthority":"firmware","components":[
            {"id":"sensor.dht22","type":"sensor","interface":"one_wire",
             "state":"active","active":true,"pins":[32]}]}""")
        repo.ingestTelemetry(
            """{"version":1,"measurements":[
               {"type":"temperature","status":"fresh","valid":true,"value":40.0,"unit":"C","timestampMs":1000}]}"""
        )
        assertEquals(1, repo.state.value.pendingAutomations.size)

        repo.runAutomations(sink = AutomationActionSink { true }, authorized = false)
        assertFalse(repo.state.value.automationEvents.any { it.status == AutomationEventStatus.PENDING })
        assertEquals(
            AutomationEventStatus.BLOCKED,
            repo.state.value.automationEvents.single().status
        )
    }

    @Test fun repositoryDispatchesPendingAutomationWhenAuthorized() = runBlocking {
        val storage = FakeComponentStatusStorage()
        val repo = ComponentStatusRepository(storage, clock = { 1000L })
        repo.setDevice("device-a")
        repo.setAutomationRules(listOf(rgbRule()))
        repo.ingest("""{"version":1,"gpioAuthority":"firmware","components":[
            {"id":"sensor.dht22","type":"sensor","interface":"one_wire",
             "state":"active","active":true,"pins":[32]}]}""")
        repo.ingestTelemetry(
            """{"version":1,"measurements":[
               {"type":"temperature","status":"fresh","valid":true,"value":40.0,"unit":"C","timestampMs":1000}]}"""
        )
        repo.runAutomations(sink = AutomationActionSink { true }, authorized = true)
        assertEquals(
            AutomationEventStatus.DISPATCHED,
            repo.state.value.automationEvents.single().status
        )
    }

    @Test fun loadPersistedSeedsDefaultAutomationsOnce() = runBlocking {
        val storage = FakeComponentStatusStorage()
        val repo = ComponentStatusRepository(storage, clock = { 1000L })
        repo.loadPersisted()
        assertTrue(repo.state.value.automationRules.isNotEmpty())
        assertTrue(repo.state.value.automationRules.none { it.isHardware })
        assertEquals(1, storage.saveAutomationRulesCount)
    }

    private fun event(rule: AutomationRule) = AutomationEvent(
        id = "${rule.id}@1000", ruleId = rule.id, componentId = rule.componentId,
        measurementType = rule.measurementType, action = rule.action, value = 40f,
        atMs = 1000L, status = AutomationEventStatus.PENDING
    )
}
