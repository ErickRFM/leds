package com.rgbcontroller

import com.rgbcontroller.core.components.alerts.AlertComparison
import com.rgbcontroller.core.components.alerts.AlertRule
import com.rgbcontroller.core.components.alerts.AlertSeverity
import com.rgbcontroller.core.components.automation.AutomationAction
import com.rgbcontroller.core.components.automation.AutomationRule
import com.rgbcontroller.core.components.calibration.StoredSoilCalibration
import com.rgbcontroller.core.components.status.LocalDesiredComponent
import com.rgbcontroller.core.export.ExportParseResult
import com.rgbcontroller.core.export.ProjectExportBuilder
import com.rgbcontroller.core.export.ProjectExportEnvelope
import com.rgbcontroller.core.export.ProjectExportParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectPortableFormatTest {

    private val components = listOf(
        LocalDesiredComponent("dht22", "sensor", listOf(32)),
        LocalDesiredComponent("led_rgb", "actuator", listOf(25, 26, 27))
    )
    private val alerts = listOf(
        AlertRule("a1", "dht22", "temperature", AlertComparison.ABOVE, 40f,
            hysteresis = 1f, minIntervalMs = 60_000, severity = AlertSeverity.CRITICAL, enabled = true)
    )
    private val automations = listOf(
        AutomationRule("m1", "dht22", "temperature", AlertComparison.ABOVE, 45f,
            action = AutomationAction.Notify("calor", AlertSeverity.WARNING), enabled = true),
        AutomationRule("m2", "soil_moisture", "soil_moisture", AlertComparison.BELOW, 20f,
            action = AutomationAction.SetRgb(255, 0, 0, 80), enabled = false)
    )
    private val calibrations = listOf(
        StoredSoilCalibration(deviceId = "device-a", dryRaw = 40, wetRaw = 1500)
    )

    private fun build() = ProjectExportBuilder.build(
        projectId = "p1", name = "Casa", boardProfile = "esp32-legacy-v1",
        capabilities = listOf("dht22", "led_rgb"), components = components,
        alerts = alerts, automations = automations, calibrations = calibrations,
        nowMs = 1_000L
    )

    @Test fun roundTripPreservesContent() {
        val serialized = build().serialize()
        val result = ProjectExportParser.parse(serialized)
        assertTrue("rejected: " + (result as? ExportParseResult.Rejected)?.reason,
            result is ExportParseResult.Valid)
        val envelope = (result as ExportParseResult.Valid).envelope
        assertEquals(build().components.sortedBy { it.id }, envelope.components.sortedBy { it.id })
        assertEquals(alerts, envelope.alerts)
        assertEquals(automations.sortedBy { it.id }, envelope.automations.sortedBy { it.id })
        assertEquals(calibrations, envelope.calibrations)
        assertEquals("esp32-legacy-v1", envelope.boardProfile)
    }

    @Test fun serializationIsDeterministicRegardlessOfInputOrder() {
        val shuffled = ProjectExportEnvelope(
            schema = ProjectExportEnvelope.SCHEMA_VERSION,
            app = ProjectExportEnvelope.APP,
            kind = ProjectExportEnvelope.KIND,
            projectId = "p1", name = "Casa", createdAtMs = 1_000L,
            boardProfile = "esp32-legacy-v1",
            capabilities = listOf("led_rgb", "dht22"),
            components = components.reversed(),
            alerts = alerts, automations = automations.reversed(), calibrations = calibrations
        )
        assertEquals(build().serialize(), shuffled.serialize())
    }

    @Test fun tamperedPayloadIsRejected() {
        val raw = build().serialize()
        val tampered = raw.replace("Casa", "Robada")
        val result = ProjectExportParser.parse(tampered)
        assertTrue(result is ExportParseResult.Rejected)
    }

    @Test fun missingChecksumIsRejected() {
        val root = JSONObject(build().serialize())
        root.remove("checksum")
        assertTrue(ProjectExportParser.parse(root.toString()) is ExportParseResult.Rejected)
    }

    @Test fun unsupportedSchemaIsRejected() {
        val root = JSONObject(build().serialize())
        root.put("schema", 99)
        assertTrue(ProjectExportParser.parse(root.toString()) is ExportParseResult.Rejected)
    }

    @Test fun unknownAppOrKindIsRejected() {
        val app = JSONObject(build().serialize()).apply { put("app", "other") }
        assertTrue(ProjectExportParser.parse(app.toString()) is ExportParseResult.Rejected)
        val kind = JSONObject(build().serialize()).apply { put("kind", "script") }
        assertTrue(ProjectExportParser.parse(kind.toString()) is ExportParseResult.Rejected)
    }

    @Test fun blankOrInvalidJsonIsRejected() {
        assertTrue(ProjectExportParser.parse(null) is ExportParseResult.Rejected)
        assertTrue(ProjectExportParser.parse("") is ExportParseResult.Rejected)
        assertTrue(ProjectExportParser.parse("not json") is ExportParseResult.Rejected)
    }

    @Test fun oversizedPayloadIsRejected() {
        val huge = build().copy(name = "x".repeat(ProjectExportEnvelope.MAX_BYTES + 1))
        val raw = huge.serialize()
        assertTrue(ProjectExportParser.parse(raw) is ExportParseResult.Rejected)
    }

    @Test fun tooManyEntriesAreRejected() {
        val many = List(ProjectExportEnvelope.MAX_ALERTS + 1) {
            AlertRule("a$it", "dht22", "temperature", AlertComparison.ABOVE, 40f)
        }
        val result = ProjectExportParser.parse(build().copy(alerts = many).serialize())
        assertTrue(result is ExportParseResult.Rejected)
    }

    @Test fun invalidContentIsRejected() {
        val duplicate = ProjectExportParser.parse(
            build().copy(components = listOf(components[0], components[0])).serialize())
        assertTrue(duplicate is ExportParseResult.Rejected)

        val badPin = ProjectExportParser.parse(
            build().copy(components = listOf(LocalDesiredComponent("dht22", "sensor", listOf(99)))).serialize())
        assertTrue(badPin is ExportParseResult.Rejected)

        val badRgb = ProjectExportParser.parse(
            build().copy(automations = listOf(
                AutomationRule("m", "dht22", "temperature", AlertComparison.ABOVE, 1f,
                    action = AutomationAction.SetRgb(300, 0, 0, 50))
            )).serialize())
        assertTrue(badRgb is ExportParseResult.Rejected)
    }

    @Test fun checksumDiffersForDifferentContent() {
        val other = build().copy(projectId = "p2")
        assertNotEquals(build().checksum(), other.checksum())
    }
}
