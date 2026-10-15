package com.rgbcontroller.core.components.status

import com.rgbcontroller.core.components.alerts.AlertRule
import com.rgbcontroller.core.components.telemetry.TelemetrySample

/**
 * Persistence boundary for the persistent component dashboard. Implementations
 * must never store credentials, owner keys or BLE UUIDs.
 */
interface ComponentStatusStorage {
    suspend fun loadLastKnown(): LastKnownStatus?
    suspend fun saveLastKnown(status: LastKnownStatus)
    suspend fun loadDesired(): LocalDesiredConfig
    suspend fun saveDesired(config: LocalDesiredConfig)
    suspend fun loadTelemetry(): LastKnownTelemetry?
    suspend fun saveTelemetry(telemetry: LastKnownTelemetry)
    suspend fun loadHistory(): List<TelemetrySample>
    suspend fun saveHistory(samples: List<TelemetrySample>)
    suspend fun loadAlertRules(): List<AlertRule>
    suspend fun saveAlertRules(rules: List<AlertRule>)
}
