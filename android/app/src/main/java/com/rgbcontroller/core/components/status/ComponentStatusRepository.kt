package com.rgbcontroller.core.components.status

import com.rgbcontroller.core.components.alerts.AlertEngine
import com.rgbcontroller.core.components.alerts.AlertRule
import com.rgbcontroller.core.components.alerts.DefaultAlertRules
import com.rgbcontroller.core.components.automation.AutomationActionSink
import com.rgbcontroller.core.components.automation.AutomationCoordinator
import com.rgbcontroller.core.components.automation.AutomationEngine
import com.rgbcontroller.core.components.automation.AutomationEventStatus
import com.rgbcontroller.core.components.automation.AutomationRule
import com.rgbcontroller.core.components.automation.DefaultAutomationRules
import com.rgbcontroller.core.components.calibration.StoredSoilCalibration
import com.rgbcontroller.core.components.model.ComponentStatusCodec
import com.rgbcontroller.core.components.model.ComponentTelemetryCodec
import com.rgbcontroller.core.components.telemetry.TelemetryHistory
import com.rgbcontroller.core.components.telemetry.TelemetryHistoryLimits
import com.rgbcontroller.core.components.telemetry.TelemetryProvenance
import com.rgbcontroller.core.export.ProjectExportEnvelope
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
        val storedRules = storage.loadAlertRules()
        val storedAutomations = storage.loadAutomationRules()
        val storedCalibration = storage.loadSoilCalibration()
        // If no history was ever recorded but a last-known snapshot exists, seed a
        // single LAST_KNOWN sample per reading so a chart is never empty-handed.
        val history = if (storedHistory.isEmpty() && telemetry != null) {
            val seeded = TelemetryHistory.append(
                existing = emptyList(),
                incoming = TelemetryHistory.samplesFromMeasurements(
                    telemetry.measurements, telemetry.deviceId,
                    telemetry.confirmedAtMs, TelemetryProvenance.LAST_KNOWN,
                    dhtComponentId = TelemetryHistory.activeDhtId(
                        lastKnown?.takeIf { it.deviceId == telemetry.deviceId }?.components
                    ) ?: "unattributed"
                ),
                limits = historyLimits,
                nowMs = telemetry.confirmedAtMs
            )
            if (seeded.isNotEmpty()) storage.saveHistory(seeded)
            seeded
        } else {
            storedHistory
        }
        val rules = storedRules.ifEmpty { DefaultAlertRules.defaults() }
        if (storedRules.isEmpty()) storage.saveAlertRules(rules)
        val automations = storedAutomations.ifEmpty { DefaultAutomationRules.defaults() }
        if (storedAutomations.isEmpty()) storage.saveAutomationRules(automations)
        _state.value = _state.value.copy(
            lastKnownStatus = lastKnown,
            desiredConfig = desired,
            lastKnownTelemetry = telemetry,
            history = history,
            alertRules = rules,
            automationRules = automations,
            soilCalibration = storedCalibration
        )
    }

    /**
     * Persist a soil calibration bound to the CURRENT device. Rejects (returns
     * false) an invalid pair or a missing device, so no calibration is stored for
     * "no device". A stored calibration for another device is kept but never
     * applied (see [ComponentDashboardState.activeSoilCalibration]).
     */
    suspend fun setSoilCalibration(dryRaw: Int, wetRaw: Int): Boolean {
        val device = _state.value.deviceId ?: return false
        val stored = runCatching {
            StoredSoilCalibration(deviceId = device, dryRaw = dryRaw, wetRaw = wetRaw)
        }.getOrNull() ?: return false
        _state.value = _state.value.copy(soilCalibration = stored)
        storage.saveSoilCalibration(stored)
        return true
    }

    suspend fun clearSoilCalibration() {
        _state.value = _state.value.copy(soilCalibration = null)
        storage.saveSoilCalibration(null)
    }

    /** Replace the configurable alert thresholds and persist them. */
    suspend fun setAlertRules(rules: List<AlertRule>) {
        _state.value = _state.value.copy(alertRules = rules)
        storage.saveAlertRules(rules)
    }

    /** Replace the configurable automations and persist them. */
    suspend fun setAutomationRules(rules: List<AutomationRule>) {
        _state.value = _state.value.copy(automationRules = rules)
        storage.saveAutomationRules(rules)
    }

    /**
     * Immutable snapshot of the importable configuration (F09). Used to roll
     * back atomically if an import fails halfway.
     */
    data class ConfigSnapshot(
        val desired: LocalDesiredConfig,
        val alerts: List<AlertRule>,
        val automations: List<AutomationRule>,
        val calibration: StoredSoilCalibration?
    )

    fun configSnapshot(): ConfigSnapshot = ConfigSnapshot(
        desired = _state.value.desiredConfig,
        alerts = _state.value.alertRules,
        automations = _state.value.automationRules,
        calibration = _state.value.soilCalibration
    )

    /** Restore a previously captured snapshot to memory and persistence. */
    suspend fun restore(snapshot: ConfigSnapshot) {
        _state.value = _state.value.copy(
            desiredConfig = snapshot.desired,
            alertRules = snapshot.alerts,
            automationRules = snapshot.automations,
            soilCalibration = snapshot.calibration
        )
        storage.saveDesired(snapshot.desired)
        storage.saveAlertRules(snapshot.alerts)
        storage.saveAutomationRules(snapshot.automations)
        storage.saveSoilCalibration(snapshot.calibration)
    }

    /** Stage a parsed project for preview; never applied until confirmed. */
    fun setImportPreview(preview: ProjectExportEnvelope?) {
        _state.value = _state.value.copy(importPreview = preview, importError = null)
    }

    fun setImportError(message: String?) {
        _state.value = _state.value.copy(importError = message, importPreview = null)
    }

    /** Clear any staged import preview/error. */
    fun clearImport() {
        _state.value = _state.value.copy(importPreview = null, importError = null)
    }

    fun setDevice(next: String?) {
        if (deviceId == next) return
        deviceId = next
        // Another device's live status, readings, alert/automation state and
        // transitions must never apply to the new device.
        _state.value = _state.value.copy(
            deviceId = next,
            firmwareStatus = null,
            firmwareTelemetry = null,
            alertStates = emptyMap(),
            alertEvents = emptyList(),
            automationStates = emptyMap(),
            automationEvents = emptyList(),
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

    /** A failed poll must never keep the previous reading labelled as live. */
    fun invalidateLiveTelemetry(message: String) {
        _state.value = _state.value.copy(
            firmwareTelemetry = null,
            authorityError = message
        )
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
            measurements, current, now, TelemetryProvenance.FIRMWARE,
            dhtComponentId = TelemetryHistory.activeDhtId(_state.value.authoritative)
                ?: "unattributed"
        )
        val history = TelemetryHistory.append(
            existing = _state.value.history,
            incoming = samples,
            limits = historyLimits,
            nowMs = now
        )
        storage.saveHistory(history)
        val previous = _state.value
        val latest = AlertEngine.latestBySeries(
            TelemetryHistory.samplesForDevice(history, this.deviceId)
        )
        val evaluation = AlertEngine.evaluate(
            rules = previous.alertRules,
            latest = latest,
            previous = previous.alertStates,
            nowMs = now
        )
        val alertEvents = (evaluation.events + previous.alertEvents).take(MAX_ALERT_EVENTS)
        val automationOutcome = AutomationEngine.evaluate(
            rules = previous.automationRules,
            latest = latest,
            previous = previous.automationStates,
            nowMs = now
        )
        val automationEvents =
            (automationOutcome.events + previous.automationEvents).take(MAX_AUTOMATION_EVENTS)
        _state.value = previous.copy(
            lastKnownTelemetry = lastKnown,
            history = history,
            alertStates = evaluation.states,
            alertEvents = alertEvents,
            automationStates = automationOutcome.states,
            automationEvents = automationEvents
        )
        return true
    }

    /**
     * Run pending automation actions. Hardware actions go only through [sink]
     * (the existing command path) and only while [authorized]; otherwise they are
     * left BLOCKED instead of silently failing or being simulated.
     */
    suspend fun runAutomations(sink: AutomationActionSink?, authorized: Boolean) {
        val pending = _state.value.pendingAutomations
        if (pending.isEmpty()) return
        val results = AutomationCoordinator.dispatch(pending, authorized, sink)
        val byId = results.associate { it.eventId to it.status }
        val updated = _state.value.automationEvents.map { event ->
            val status = byId[event.id]
            if (status != null && status != AutomationEventStatus.PENDING) {
                event.copy(status = status)
            } else {
                event
            }
        }
        _state.value = _state.value.copy(automationEvents = updated)
    }

    private companion object {
        const val MAX_ALERT_EVENTS = 200
        const val MAX_AUTOMATION_EVENTS = 200
    }
}
