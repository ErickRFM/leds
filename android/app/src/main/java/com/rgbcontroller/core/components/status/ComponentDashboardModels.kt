package com.rgbcontroller.core.components.status

import com.rgbcontroller.core.components.model.ComponentRuntimeStatus

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
}
