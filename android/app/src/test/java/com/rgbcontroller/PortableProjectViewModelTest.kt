package com.rgbcontroller

import com.rgbcontroller.core.components.alerts.AlertComparison
import com.rgbcontroller.core.components.alerts.AlertRule
import com.rgbcontroller.core.components.alerts.AlertSeverity
import com.rgbcontroller.core.components.automation.AutomationAction
import com.rgbcontroller.core.components.automation.AutomationRule
import com.rgbcontroller.core.components.status.ComponentStatusRepository
import com.rgbcontroller.core.components.status.LocalDesiredComponent
import com.rgbcontroller.core.components.status.LocalDesiredConfig
import com.rgbcontroller.core.export.ProjectExportBuilder
import com.rgbcontroller.feature.hardwarelab.ComponentDashboardViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PortableProjectViewModelTest {

    private class Harness(
        storage: FakeComponentStatusStorage = FakeComponentStatusStorage(),
        var device: String? = "device-a"
    ) {
        var savedLocation: String? = null
        var savedText: String? = null
        var readText: String? = null
        val repo = ComponentStatusRepository(storage, clock = { 1000L })
        val viewModel = ComponentDashboardViewModel(
            repository = repo,
            isLinkAuthorized = { true },
            currentDeviceId = { device },
            fetchStatus = { null },
            readProjectText = { readText },
            writeProjectText = { location, text ->
                savedLocation = location
                savedText = text
                true
            },
            nowMs = { 1234L }
        )
    }

    private fun sampleAlert(id: String = "alert-1") = AlertRule(
        id = id,
        componentId = "dht22",
        measurementType = "temperature",
        comparison = AlertComparison.ABOVE,
        threshold = 35f,
        hysteresis = 1f,
        minIntervalMs = 30_000L,
        severity = AlertSeverity.WARNING,
        enabled = true
    )

    private fun sampleAutomation(id: String = "auto-1") = AutomationRule(
        id = id,
        componentId = "soil_moisture",
        measurementType = "soil_moisture",
        comparison = AlertComparison.BELOW,
        threshold = 20f,
        action = AutomationAction.Notify("regar", AlertSeverity.CRITICAL),
        enabled = true
    )

    private suspend fun Harness.seed() {
        viewModel.onConnectionChanged() // set device
        viewModel.setLocalDesired(
            LocalDesiredConfig(listOf(LocalDesiredComponent("dht22", "sensor", listOf(32))))
        )
        viewModel.setAlertRules(listOf(sampleAlert()))
        viewModel.setAutomationRules(listOf(sampleAutomation()))
        viewModel.setSoilCalibration(1500, 3200)
    }

    @Test
    fun exportWritesDeterministicFileWithoutSecrets() = runBlocking {
        val h = Harness()
        h.seed()
        assertTrue(h.viewModel.exportTo("content://file/project.json"))
        assertEquals("content://file/project.json", h.savedLocation)
        val text = h.savedText!!
        assertTrue(text.contains("\"app\":\"compi\""))
        assertTrue(text.contains("\"kind\":\"project\""))
        assertFalse(text.lowercase().contains("password"))
        assertFalse(text.lowercase().contains("token"))
        assertFalse(text.lowercase().contains("\"key\""))

        val second = Harness()
        second.seed()
        second.viewModel.exportTo("content://file/project.json")
        assertEquals(text, second.savedText)
    }

    @Test
    fun readImportStagesPreviewAndAppliesReplace() = runBlocking {
        val source = Harness()
        source.seed()
        source.viewModel.exportTo("content://file/project.json")

        val target = Harness()
        target.viewModel.onConnectionChanged()
        target.readText = source.savedText
        target.viewModel.readImport("content://file/project.json")
        assertNotNull(target.repo.state.value.importPreview)

        assertTrue(target.viewModel.applyImport(ComponentDashboardViewModel.ImportMode.REPLACE))
        val state = target.repo.state.value
        assertEquals(listOf("dht22"), state.desiredConfig.components.map { it.id })
        assertEquals(listOf("alert-1"), state.alertRules.map { it.id })
        assertEquals(listOf("auto-1"), state.automationRules.map { it.id })
        assertEquals(1500, state.soilCalibration?.dryRaw)
        assertEquals(3200, state.soilCalibration?.wetRaw)
        assertNull(state.importPreview)
    }

    @Test
    fun importAsCopyRenamesConflictingIds() = runBlocking {
        val source = Harness()
        source.seed()
        source.viewModel.exportTo("content://file/project.json")

        val target = Harness()
        target.seed() // already has dht22 / alert-1 / auto-1
        target.readText = source.savedText
        target.viewModel.readImport("content://file/project.json")
        assertTrue(target.viewModel.applyImport(ComponentDashboardViewModel.ImportMode.COPY))

        val state = target.repo.state.value
        assertEquals(listOf("dht22", "imp_dht22"), state.desiredConfig.components.map { it.id })
        assertEquals(listOf("alert-1", "imp_alert-1"), state.alertRules.map { it.id })
        assertEquals(listOf("auto-1", "imp_auto-1"), state.automationRules.map { it.id })
    }

    @Test
    fun rejectedImportNeverTouchesConfiguration() = runBlocking {
        val h = Harness()
        h.seed()
        val before = h.repo.state.value.desiredConfig
        h.readText = "{ esto no es un proyecto valido"
        h.viewModel.readImport("content://file/broken.json")
        val state = h.repo.state.value
        assertNull(state.importPreview)
        assertNotNull(state.importError)
        assertEquals(before, state.desiredConfig)
        assertEquals(listOf("alert-1"), state.alertRules.map { it.id })
    }

    @Test
    fun failedApplyRollsBackToPreviousConfiguration() = runBlocking {
        val storage = FailingOnceStorage()
        val h = Harness(storage)
        h.seed()
        h.viewModel.exportTo("content://file/project.json")
        val stored = h.savedText

        // Second harness with the imported payload but a storage that fails once.
        val target = Harness(storage)
        target.seed()
        target.readText = stored
        target.viewModel.readImport("content://file/project.json")

        storage.failNextAlertSave = true
        val applied = target.viewModel.applyImport(ComponentDashboardViewModel.ImportMode.REPLACE)
        assertFalse(applied)
        val state = target.repo.state.value
        // Original config is restored after the failed import.
        assertEquals(listOf("alert-1"), state.alertRules.map { it.id })
        assertEquals(listOf("dht22"), state.desiredConfig.components.map { it.id })
    }

    @Test
    fun cancelImportDropsStagedPreview() = runBlocking {
        val source = Harness()
        source.seed()
        source.viewModel.exportTo("content://file/project.json")

        val target = Harness()
        target.readText = source.savedText
        target.viewModel.readImport("content://file/project.json")
        assertNotNull(target.repo.state.value.importPreview)
        target.viewModel.cancelImport()
        assertNull(target.repo.state.value.importPreview)
        assertNull(target.repo.state.value.importError)
    }

    @Test
    fun buildEnvelopeCarriesBoardProfileAndCapabilities() {
        val h = Harness()
        runBlocking { h.seed() }
        val envelope = h.viewModel.buildExportEnvelope()
        assertEquals(ComponentDashboardViewModel.DEFAULT_BOARD_PROFILE, envelope.boardProfile)
        assertEquals(listOf("sensor"), envelope.capabilities)
        assertEquals("device-a", envelope.projectId)
        assertEquals(1234L, envelope.createdAtMs)
    }

    @Test
    fun envelopeCanBeRebuiltFromStoredComponents() {
        val envelope = ProjectExportBuilder.build(
            projectId = "p",
            name = "n",
            boardProfile = "esp32-legacy-v1",
            capabilities = emptyList(),
            components = emptyList(),
            alerts = emptyList(),
            automations = emptyList(),
            calibrations = emptyList(),
            nowMs = 1L
        )
        assertEquals("compi", envelope.app)
        assertEquals("project", envelope.kind)
    }

    @Test
    fun remapOnImportAppliesSafePins() = runBlocking {
        val source = Harness()
        source.viewModel.onConnectionChanged()
        source.viewModel.setLocalDesired(
            LocalDesiredConfig(listOf(LocalDesiredComponent("dht22", "sensor", listOf(10))))
        )
        source.viewModel.exportTo("content://file/remap.json")
        val text = source.savedText!!

        val target = Harness()
        target.viewModel.onConnectionChanged()
        target.readText = text
        target.viewModel.readImport("content://file/remap.json")
        assertEquals(
            com.rgbcontroller.core.export.CompatibilityLevel.REQUIRES_REMAP,
            target.viewModel.compatibilityReport()?.level
        )
        assertTrue(target.viewModel.applyImport(ComponentDashboardViewModel.ImportMode.REPLACE))
        val pins = target.repo.state.value.desiredConfig.components.single().pins
        assertTrue(pins.none { it in setOf(6, 7, 8, 9, 10, 11) })
    }

    @Test
    fun incompatibleImportIsRefusedWithoutChanges() = runBlocking {
        val h = Harness()
        h.seed()
        val before = h.repo.state.value.desiredConfig
        h.readText = ProjectExportBuilder.build(
            projectId = "p",
            name = "n",
            boardProfile = "esp32-legacy-v1",
            capabilities = emptyList(),
            components = listOf(LocalDesiredComponent("weird", "quantum", listOf(32))),
            alerts = emptyList(),
            automations = emptyList(),
            calibrations = emptyList(),
            nowMs = 1L
        ).serialize()
        h.viewModel.readImport("content://file/project.json")
        assertFalse(h.viewModel.applyImport(ComponentDashboardViewModel.ImportMode.REPLACE))
        assertEquals(before, h.repo.state.value.desiredConfig)
        assertNotNull(h.repo.state.value.importError)
    }

    @Test
    fun importedReservedPinIsRemappedToSafePin() {
        val envelope = ProjectExportBuilder.build(
            projectId = "p",
            name = "n",
            boardProfile = "esp32-legacy-v1",
            capabilities = emptyList(),
            components = listOf(LocalDesiredComponent("dht22", "sensor", listOf(10))),
            alerts = emptyList(),
            automations = emptyList(),
            calibrations = emptyList(),
            nowMs = 1L
        )
        val report = com.rgbcontroller.core.export.BoardCompatibilityEngine.evaluate(
            envelope,
            com.rgbcontroller.core.export.BoardProfiles.ESP32_LEGACY_V1
        )
        assertEquals(com.rgbcontroller.core.export.CompatibilityLevel.REQUIRES_REMAP, report.level)
        assertTrue(report.remapped.single().toPins.none { it in setOf(6, 7, 8, 9, 10, 11) })
    }
}

/** Storage double that fails a single alert-rule save, to exercise rollback. */
private class FailingOnceStorage : FakeComponentStatusStorage() {
    var failNextAlertSave = false
    override suspend fun saveAlertRules(rules: List<AlertRule>) {
        if (failNextAlertSave) {
            failNextAlertSave = false
            throw IllegalStateException("disk full")
        }
        super.saveAlertRules(rules)
    }
}
