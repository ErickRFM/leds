package com.rgbcontroller

import com.rgbcontroller.core.components.status.ComponentStatusStorage
import com.rgbcontroller.core.components.status.LastKnownStatus
import com.rgbcontroller.core.components.status.LastKnownTelemetry
import com.rgbcontroller.core.components.alerts.AlertRule
import com.rgbcontroller.core.components.automation.AutomationRule
import com.rgbcontroller.core.components.calibration.StoredSoilCalibration
import com.rgbcontroller.core.components.status.LocalDesiredConfig
import com.rgbcontroller.core.components.telemetry.TelemetrySample

/** In-memory storage double used by dashboard tests. No Android required. */
class FakeComponentStatusStorage : ComponentStatusStorage {
    var lastKnown: LastKnownStatus? = null
    var desired: LocalDesiredConfig = LocalDesiredConfig.EMPTY
    var lastKnownTelemetry: LastKnownTelemetry? = null
    var history: List<TelemetrySample> = emptyList()
    var alertRules: List<AlertRule> = emptyList()
    var automationRules: List<AutomationRule> = emptyList()
    var soilCalibration: StoredSoilCalibration? = null
    var saveLastKnownCount = 0
    var saveDesiredCount = 0
    var saveTelemetryCount = 0
    var saveHistoryCount = 0
    var saveAlertRulesCount = 0
    var saveAutomationRulesCount = 0
    var saveSoilCalibrationCount = 0

    override suspend fun loadLastKnown(): LastKnownStatus? = lastKnown

    override suspend fun saveLastKnown(status: LastKnownStatus) {
        lastKnown = status
        saveLastKnownCount++
    }

    override suspend fun loadDesired(): LocalDesiredConfig = desired

    override suspend fun saveDesired(config: LocalDesiredConfig) {
        desired = config
        saveDesiredCount++
    }

    override suspend fun loadTelemetry(): LastKnownTelemetry? = lastKnownTelemetry

    override suspend fun saveTelemetry(telemetry: LastKnownTelemetry) {
        lastKnownTelemetry = telemetry
        saveTelemetryCount++
    }

    override suspend fun loadHistory(): List<TelemetrySample> = history

    override suspend fun saveHistory(samples: List<TelemetrySample>) {
        history = samples
        saveHistoryCount++
    }

    override suspend fun loadAlertRules(): List<AlertRule> = alertRules

    override suspend fun saveAlertRules(rules: List<AlertRule>) {
        alertRules = rules
        saveAlertRulesCount++
    }

    override suspend fun loadAutomationRules(): List<AutomationRule> = automationRules

    override suspend fun saveAutomationRules(rules: List<AutomationRule>) {
        automationRules = rules
        saveAutomationRulesCount++
    }

    override suspend fun loadSoilCalibration(): StoredSoilCalibration? = soilCalibration

    override suspend fun saveSoilCalibration(calibration: StoredSoilCalibration?) {
        soilCalibration = calibration
        saveSoilCalibrationCount++
    }
}
