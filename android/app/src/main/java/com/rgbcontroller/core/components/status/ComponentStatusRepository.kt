package com.rgbcontroller.core.components.status

import com.rgbcontroller.core.components.model.ComponentStatusCodec
import com.rgbcontroller.core.components.model.ComponentTelemetryCodec
import com.rgbcontroller.core.components.telemetry.TelemetryHistory
import com.rgbcontroller.core.components.telemetry.TelemetryHistoryLimits
import com.rgbcontroller.core.components.telemetry.TelemetryProvenance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for the component dashboard. It keeps three concepts
 * strictly separate:
 *  - [LocalDesiredConfig]: what the user asked for locally (never acceptance).
 *  - [FirmwareReportedStatus]: what the firmware confirmed right now.
 *  - [LastKnownStatus]: the last confirmed status, persisted across sessions.
 *
 * Invariants:
 *  - An invalid/truncated/unauthoritative payload never overwrites confirmed data.
 *  - Losing the link revokes authoritative status (no live data while offline).
 *  - A device change never carries another device's authoritative status over.
 */
class ComponentStatusRepository(
    private val storage: ComponentStatusStorage,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val historyLimits: TelemetryHistoryLimits = TelemetryHistoryLimits()
) {
    private val _state = MutableStateFlow(ComponentDashboardState())
    val state: StateFlow<ComponentDashboardState> = _state.asStateFlow()

    private var deviceId: String? = null

    /** Restore persisted last-known status, telemetry, history and desired config. */
    suspend fun loadPersisted() {
        val lastKnown = storage.loadLastKnown()
        val desired = storage.loadDesired()
        val telemetry = storage.loadTelemetry()
        val storedHistory = storage.loadHistory()
        // If no history was ever recorded but a last-known snapshot exists, seed a
        // single LAST_KNOWN sample per reading so a chart is never empty-handed.
        val history = if (storedHistory.isEmpty() && telemetry != null) {
            val seeded = TelemetryHistory.append(
                existing = emptyList(),
                incoming = TelemetryHistory.samplesFromMeasurements(
                    telemetry.measurements, telemetry.deviceId,
                    telemetry.confirmedAtMs, TelemetryProvenance.LAST_KNOWN
                ),
                limits = historyLimits,
                nowMs = telemetry.confirmedAtMs
            )
            if (seeded.isNotEmpty()) storage.saveHistory(seeded)
            seeded
        } else {
            storedHistory
        }
        _state.value = _state.value.copy(
            lastKnownStatus = lastKnown,
            desiredConfig = desired,
            lastKnownTelemetry = telemetry,
            history = history
        )
    }

    fun setDevice(next: String?) {
        if (deviceId == next) return
        deviceId = next
        // Another device's live status/readings must never apply to the new device.
        _state.value = _state.value.copy(
            deviceId = next,
            firmwareStatus = null,
            firmwareTelemetry = null,
            authorityError = null
        )
    }

    fun setConnected(connected: Boolean) {
        if (_state.value.connected == connected) return
        _state.value = if (connected) {
            _state.value.copy(connected = true)
        } else {
            // Offline: drop authoritative status/readings so nothing is shown as live.
            _state.value.copy(
                connected = false,
                firmwareStatus = null,
                firmwareTelemetry = null
            )
        }
    }

    fun reportError(message: String) {
        _state.value = _state.value.copy(authorityError = message)
    }

    suspend fun setDesired(config: LocalDesiredConfig) {
        _state.value = _state.value.copy(desiredConfig = config)
        storage.saveDesired(config)
    }

    /**
     * Accept a firmware status payload. Returns true only when the payload was
     * authoritative and readable; otherwise the previous confirmed data and the
     * last-known snapshot remain untouched.
     */
    suspend fun ingest(raw: String?): Boolean {
        val current = deviceId
        if (current == null) {
            reportError("Dispositivo sin identificar; no se puede confirmar el estado.")
            return false
        }
        val components = ComponentStatusCodec.decode(raw)
        if (components == null) {
            reportError("Estado de componentes no autoritativo, vacío o ilegible.")
            return false
        }
        val now = clock()
        val reported = FirmwareReportedStatus(current, components, now)
        _state.value = _state.value.copy(firmwareStatus = reported, authorityError = null)
        val lastKnown = LastKnownStatus(current, components, now)
        storage.saveLastKnown(lastKnown)
        _state.value = _state.value.copy(lastKnownStatus = lastKnown)
        return true
    }

    /**
     * Accept a firmware telemetry payload. Returns true only when the document
     * was well-formed (an empty but valid list counts). An unreadable payload
     * never overwrites confirmed readings and is surfaced as an error instead of
     * fabricating numbers.
     */
    suspend fun ingestTelemetry(raw: String?): Boolean {
        val current = deviceId
        if (current == null) {
            reportError("Dispositivo sin identificar; no se pueden confirmar lecturas.")
            return false
        }
        val measurements = ComponentTelemetryCodec.decode(raw)
        if (measurements == null) {
            reportError("Telemetría de sensores no autoritativa, vacía o ilegible.")
            return false
        }
        val now = clock()
        val reported = FirmwareReportedTelemetry(current, measurements, now)
        _state.value = _state.value.copy(firmwareTelemetry = reported, authorityError = null)
        val lastKnown = LastKnownTelemetry(current, measurements, now)
        storage.saveTelemetry(lastKnown)
        val samples = TelemetryHistory.samplesFromMeasurements(
            measurements, current, now, TelemetryProvenance.FIRMWARE
        )
        val history = TelemetryHistory.append(
            existing = _state.value.history,
            incoming = samples,
            limits = historyLimits,
            nowMs = now
        )
        storage.saveHistory(history)
        _state.value = _state.value.copy(lastKnownTelemetry = lastKnown, history = history)
        return true
    }
}
