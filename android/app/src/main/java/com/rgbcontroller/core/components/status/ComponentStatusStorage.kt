package com.rgbcontroller.core.components.status

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
}
