package com.rgbcontroller.core.components.status

import com.rgbcontroller.core.components.model.ComponentMeasurement
import com.rgbcontroller.core.components.model.ComponentRuntimeStatus
import com.rgbcontroller.core.components.registry.ComponentCatalog

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

/** The single reactive state rendered by the persistent component dashboard. */
data class ComponentDashboardState(
    val deviceId: String? = null,
    val desiredConfig: LocalDesiredConfig = LocalDesiredConfig.EMPTY,
    val firmwareStatus: FirmwareReportedStatus? = null,
    val lastKnownStatus: LastKnownStatus? = null,
    val firmwareTelemetry: FirmwareReportedTelemetry? = null,
    val lastKnownTelemetry: LastKnownTelemetry? = null,
    val connected: Boolean = false,
    val authorityError: String? = null
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
}
