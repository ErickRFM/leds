package com.rgbcontroller.core.components.status

import com.rgbcontroller.core.components.alerts.AlertEvent
import com.rgbcontroller.core.components.alerts.AlertRule
import com.rgbcontroller.core.components.alerts.AlertState
import com.rgbcontroller.core.components.alerts.DefaultAlertRules
import com.rgbcontroller.core.components.automation.AutomationEvent
import com.rgbcontroller.core.components.automation.AutomationEventStatus
import com.rgbcontroller.core.components.automation.AutomationRule
import com.rgbcontroller.core.components.automation.DefaultAutomationRules
import com.rgbcontroller.core.components.calibration.StoredSoilCalibration
import com.rgbcontroller.core.components.model.ComponentMeasurement
import com.rgbcontroller.core.components.model.ComponentRuntimeStatus
import com.rgbcontroller.core.components.registry.ComponentCatalog
import com.rgbcontroller.core.components.telemetry.TelemetryHistory
import com.rgbcontroller.core.export.ProjectExportEnvelope
import com.rgbcontroller.core.components.telemetry.TelemetrySample

/**
 * Local configuration the user requested. This is a DESIRE, never proof that the
 * ESP32 accepted it: it must never be merged with [FirmwareReportedStatus].
 */
data class LocalDesiredComponent(
    val id: String,
    val type: String = "unknown",
    val pins: List<Int> = emptyList()
)

data class LocalDesiredConfig(
    val components: List<LocalDesiredComponent> = emptyList()
) {
    companion object {
        val EMPTY = LocalDesiredConfig()
    }
}

/** Authoritative status the firmware confirmed during the current session. */
data class FirmwareReportedStatus(
    val deviceId: String,
    val components: List<ComponentRuntimeStatus>,
    val receivedAtMs: Long
)

/**
 * Real sensor readings the firmware confirmed during the current session. An
 * invalid/absent measurement is kept with [ComponentMeasurement.valid] false so
 * the UI never upgrades it to a live number.
 */
data class FirmwareReportedTelemetry(
    val deviceId: String,
    val measurements: List<ComponentMeasurement>,
    val receivedAtMs: Long
)

/** Last real firmware readings, persisted across sessions and explicitly not live. */
data class LastKnownTelemetry(
    val deviceId: String,
    val measurements: List<ComponentMeasurement>,
    val confirmedAtMs: Long
)

/** Persisted copy of a previously firmware-confirmed status. */
data class LastKnownStatus(
    val deviceId: String,
    val components: List<ComponentRuntimeStatus>,
    val confirmedAtMs: Long
)

enum class ComponentStatusFreshness { LIVE, LAST_KNOWN, NONE }

data class FirmwareVoiceEvent(val id: Long, val cue: Int, val atMs: Long)

/** The single reactive state rendered by the persistent component dashboard. */
data class ComponentDashboardState(
    val deviceId: String? = null,
    val desiredConfig: LocalDesiredConfig = LocalDesiredConfig.EMPTY,
    val firmwareStatus: FirmwareReportedStatus? = null,
    val lastKnownStatus: LastKnownStatus? = null,
    val firmwareTelemetry: FirmwareReportedTelemetry? = null,
    val firmwareVoiceEvent: FirmwareVoiceEvent? = null,
    val lastKnownTelemetry: LastKnownTelemetry? = null,
    val history: List<TelemetrySample> = emptyList(),
    val alertRules: List<AlertRule> = DefaultAlertRules.defaults(),
    val alertStates: Map<String, AlertState> = emptyMap(),
    val alertEvents: List<AlertEvent> = emptyList(),
    val automationRules: List<AutomationRule> = DefaultAutomationRules.defaults(),
    val automationStates: Map<String, AlertState> = emptyMap(),
    val automationEvents: List<AutomationEvent> = emptyList(),
    val soilCalibration: StoredSoilCalibration? = null,
    val connected: Boolean = false,
    val authorityError: String? = null,
    /** Pending import preview (F09): parsed but not yet applied. */
    val importPreview: ProjectExportEnvelope? = null,
    val importError: String? = null
) {
    val freshness: ComponentStatusFreshness
        get() = when {
            authoritative != null -> ComponentStatusFreshness.LIVE
            persisted != null -> ComponentStatusFreshness.LAST_KNOWN
            else -> ComponentStatusFreshness.NONE
        }

    /**
     * Authoritative components for the CURRENT device only. Never returns the
     * last-known values, so callers cannot display persisted data as live.
     */
    val authoritative: List<ComponentRuntimeStatus>?
        get() = firmwareStatus?.takeIf { it.deviceId == deviceId }?.components

    /** Persisted components for the CURRENT device, explicitly not live. */
    val persisted: List<ComponentRuntimeStatus>?
        get() = lastKnownStatus?.takeIf { it.deviceId == deviceId }?.components

    /**
     * Catalog ids the firmware confirmed for the current device, with firmware
     * ids (`sensor.dht22`) mapped to catalog ids (`dht22`). This is the only
     * correct way to compare a local desired configuration against the
     * firmware report, since it never carries the catalog id directly.
     */
    val confirmedCatalogIds: Set<String>
        get() = (authoritative ?: persisted).orEmpty()
            // A compiled driver in CATALOG state is not a detected, connected
            // or GPIO-leased sensor. Only actual runtime activation or
            // firmware detection may qualify a module as confirmed.
            .filter { it.active || it.state == "detected" }
            .map { ComponentCatalog.catalogIdForFirmwareId(it.id) }
            .toSet()

    /**
     * Real live readings for the CURRENT device only. Never returns persisted
     * values, so callers cannot display cached telemetry as live.
     */
    val authoritativeTelemetry: List<ComponentMeasurement>?
        get() = firmwareTelemetry?.takeIf { it.deviceId == deviceId }?.measurements

    /** Persisted real readings for the CURRENT device, explicitly not live. */
    val persistedTelemetry: List<ComponentMeasurement>?
        get() = lastKnownTelemetry?.takeIf { it.deviceId == deviceId }?.measurements

    /** Freshness of the readings: LIVE only with current-device live telemetry. */
    val telemetryFreshness: ComponentStatusFreshness
        get() = when {
            authoritativeTelemetry != null -> ComponentStatusFreshness.LIVE
            persistedTelemetry != null -> ComponentStatusFreshness.LAST_KNOWN
            else -> ComponentStatusFreshness.NONE
        }

    /** Bounded telemetry history for the CURRENT device only (never another device's). */
    val deviceHistory: List<TelemetrySample>
        get() = TelemetryHistory.samplesForDevice(history, deviceId)

    /** One series of the current device's history, oldest first. */
    fun series(componentId: String, measurementType: String): List<TelemetrySample> =
        TelemetryHistory.samplesForSeries(deviceHistory, componentId, measurementType)

    /** Rules currently tripped, with the last value seen for each. */
    val activeAlerts: List<Pair<AlertRule, AlertState>>
        get() = alertRules
            .mapNotNull { rule -> alertStates[rule.id]?.takeIf { it.active }?.let { rule to it } }

    /** Most recent alert transitions first, bounded. */
    val recentAlertEvents: List<AlertEvent>
        get() = alertEvents.take(MAX_ALERT_EVENTS)

    /** Automation actions still waiting for the transport to run them. */
    val pendingAutomations: List<AutomationEvent>
        get() = automationEvents.filter { it.status == AutomationEventStatus.PENDING }

    /**
     * Soil calibration usable for the CURRENT device. A record captured on
     * another device (or an unsupported version) is treated as incompatible and
     * returned as null instead of being applied to the wrong probe.
     */
    val activeSoilCalibration: com.rgbcontroller.core.sensors.SoilCalibration?
        get() = deviceId?.let { id ->
            soilCalibration?.takeIf { it.appliesTo(id) }?.toCalibration()
        }

    /** True when a calibration exists but does not belong to the current device. */
    val soilCalibrationIncompatible: Boolean
        get() = soilCalibration != null && activeSoilCalibration == null

    companion object {
        const val MAX_ALERT_EVENTS = 50
    }
}
