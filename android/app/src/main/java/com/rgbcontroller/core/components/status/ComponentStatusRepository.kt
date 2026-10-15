package com.rgbcontroller.core.components.status

import com.rgbcontroller.core.components.model.ComponentStatusCodec
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
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val _state = MutableStateFlow(ComponentDashboardState())
    val state: StateFlow<ComponentDashboardState> = _state.asStateFlow()

    private var deviceId: String? = null

    /** Restore persisted last-known status and local desired configuration. */
    suspend fun loadPersisted() {
        val lastKnown = storage.loadLastKnown()
        val desired = storage.loadDesired()
        _state.value = _state.value.copy(lastKnownStatus = lastKnown, desiredConfig = desired)
    }

    fun setDevice(next: String?) {
        if (deviceId == next) return
        deviceId = next
        // Another device's live status must never apply to the new device.
        _state.value = _state.value.copy(
            deviceId = next,
            firmwareStatus = null,
            authorityError = null
        )
    }

    fun setConnected(connected: Boolean) {
        if (_state.value.connected == connected) return
        _state.value = if (connected) {
            _state.value.copy(connected = true)
        } else {
            // Offline: drop authoritative status so nothing is shown as live.
            _state.value.copy(connected = false, firmwareStatus = null)
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
}
