package com.rgbcontroller.feature.hardwarelab

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.core.ble.BleConnectionState
import com.rgbcontroller.core.ble.BleSecurityState
import com.rgbcontroller.core.ble.BleSnapshotAssembler
import com.rgbcontroller.core.components.alerts.AlertRule
import com.rgbcontroller.core.components.automation.AutomationAction
import com.rgbcontroller.core.components.automation.AutomationActionSink
import com.rgbcontroller.core.components.automation.AutomationRule
import com.rgbcontroller.core.components.status.ComponentDashboardState
import com.rgbcontroller.core.components.status.ComponentStatusRepository
import com.rgbcontroller.core.components.status.LocalDesiredComponent
import com.rgbcontroller.core.components.status.LocalDesiredConfig
import com.rgbcontroller.core.data.DataStoreComponentStatusStorage
import com.rgbcontroller.core.model.RgbDeviceCommand
import com.rgbcontroller.core.model.RgbReadback
import com.rgbcontroller.core.ble.MediaControlProtocol
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import com.rgbcontroller.core.export.ExportParseResult
import com.rgbcontroller.core.export.BoardCompatibilityEngine
import com.rgbcontroller.core.export.BoardProfile
import com.rgbcontroller.core.export.BoardProfiles
import com.rgbcontroller.core.export.CompatibilityLevel
import com.rgbcontroller.core.export.CompatibilityReport
import com.rgbcontroller.core.export.ProjectExportBuilder
import com.rgbcontroller.core.export.ProjectExportEnvelope
import com.rgbcontroller.core.export.ProjectExportParser
import com.rgbcontroller.core.wifi.CompiLabSession
import com.rgbcontroller.core.wifi.LabConnectionState
import com.rgbcontroller.core.wifi.LabTransport
import com.rgbcontroller.core.wifi.LocalLabClient
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.cancellation.CancellationException

/**
 * Persistent component dashboard. All state and transitions live in
 * [ComponentStatusRepository]; this ViewModel only wires transport (authorization,
 * device identity, firmware fetch). It never reads local success as acceptance.
 */
class ComponentDashboardViewModel(
    private val repository: ComponentStatusRepository,
    private val isLinkAuthorized: () -> Boolean,
    private val currentDeviceId: () -> String?,
    private val fetchStatus: suspend () -> String?,
    private val fetchTelemetry: suspend () -> String? = { null },
    private val automationSink: AutomationActionSink? = null,
    private val readProjectText: suspend (String) -> String? = { null },
    private val writeProjectText: suspend (String, String) -> Boolean = { _, _ -> false },
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    /** How an imported project is applied. */
    enum class ImportMode { REPLACE, COPY }

    val uiState: StateFlow<ComponentDashboardState> = repository.state

    suspend fun loadPersisted() = repository.loadPersisted()

    /** Re-evaluate connection and refresh authoritative status when authorized. */
    suspend fun onConnectionChanged() {
        repository.setDevice(currentDeviceId())
        val connected = isLinkAuthorized()
        repository.setConnected(connected)
        if (connected) refresh()
    }

    suspend fun setSoilCalibration(dryRaw: Int, wetRaw: Int): Boolean {
        repository.setDevice(currentDeviceId())
        return repository.setSoilCalibration(dryRaw, wetRaw)
    }

    suspend fun setAlertRules(rules: List<AlertRule>) = repository.setAlertRules(rules)

    suspend fun setAutomationRules(rules: List<AutomationRule>) =
        repository.setAutomationRules(rules)

    suspend fun refresh() {
        repository.setDevice(currentDeviceId())
        if (!isLinkAuthorized()) {
            repository.setConnected(false)
            repository.reportError("Sin conexión autorizada con el ESP32.")
            return
        }
        repository.setConnected(true)
        val raw = try {
            fetchStatus()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            repository.invalidateLiveStatus(failure.message ?: "Error de comunicación con el ESP32.")
            return
        }
        if (raw == null) {
            repository.invalidateLiveStatus("El firmware no respondió con un estado de componentes.")
            return
        }
        if (!repository.ingest(raw)) {
            repository.invalidateLiveStatus("El firmware entregó un estado de componentes inválido.")
            return
        }
        // Telemetry is secondary: a failure must never discard confirmed status,
        // and absent readings must never be invented.
        val telemetry = try {
            fetchTelemetry()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            repository.invalidateLiveTelemetry(failure.message ?: "Error al leer la telemetría del ESP32.")
            return
        }
        if (telemetry == null) {
            repository.invalidateLiveTelemetry("El firmware no respondió con telemetría actual.")
            return
        }
        if (repository.ingestTelemetry(telemetry)) {
            repository.runAutomations(automationSink, isLinkAuthorized())
        } else {
            repository.invalidateLiveTelemetry("El firmware entregó telemetría inválida.")
        }
    }

    suspend fun setLocalDesired(config: LocalDesiredConfig) = repository.setDesired(config)



    suspend fun clearSoilCalibration() = repository.clearSoilCalibration()

    // --- F09: local project import/export (offline, no secrets) --------------

    /** Build a portable envelope from the current local configuration. */
    fun buildExportEnvelope(name: String = DEFAULT_PROJECT_NAME): ProjectExportEnvelope {
        val current = repository.state.value
        return ProjectExportBuilder.build(
            projectId = currentDeviceId() ?: DEFAULT_PROJECT_ID,
            name = name,
            boardProfile = DEFAULT_BOARD_PROFILE,
            capabilities = current.desiredConfig.components.map { it.type }.distinct().sorted(),
            components = current.desiredConfig.components,
            alerts = current.alertRules,
            automations = current.automationRules,
            calibrations = listOfNotNull(current.soilCalibration),
            nowMs = nowMs()
        )
    }

    /**
     * Serialize the current configuration and write it to [location] (a SAF
     * document). Returns false if the write fails. Never includes keys/tokens.
     */
    suspend fun exportTo(location: String): Boolean {
        val payload = try {
            buildExportEnvelope().serialize()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            repository.setImportError(failure.message ?: "No se pudo preparar el proyecto.")
            return false
        }
        val written = try {
            writeProjectText(location, payload)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            repository.setImportError(failure.message ?: "No se pudo guardar el proyecto.")
            return false
        }
        if (!written) {
            repository.setImportError("No se pudo guardar el proyecto en la ubicación elegida.")
        }
        return written
    }

    /**
     * Read and strictly parse a project at [location] into a preview. A rejected
     * file never modifies the current configuration.
     */
    suspend fun readImport(location: String) {
        val raw = try {
            readProjectText(location)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            repository.setImportError(failure.message ?: "No se pudo leer el archivo.")
            return
        }
        when (val result = ProjectExportParser.parse(raw)) {
            is ExportParseResult.Valid -> repository.setImportPreview(result.envelope)
            is ExportParseResult.Rejected -> repository.setImportError(result.reason)
        }
    }

    fun cancelImport() = repository.clearImport()

    /** The board the app assumes for compatibility checks. */
    fun currentBoardProfile(): BoardProfile = BoardProfiles.find(DEFAULT_BOARD_PROFILE)

    /** Compatibility of the staged import against the current board, if any. */
    fun compatibilityReport(): CompatibilityReport? =
        repository.state.value.importPreview?.let {
            BoardCompatibilityEngine.evaluate(it, currentBoardProfile())
        }

    /**
     * Apply the staged preview. On REPLACE the current config is replaced; on
     * COPY imported entries are appended (ids de-duplicated). Pin conflicts are
     * remapped to safe pins (never reserved); an incompatible project is
     * refused. Any failure rolls the configuration back.
     */
    suspend fun applyImport(mode: ImportMode): Boolean {
        val preview = repository.state.value.importPreview ?: return false
        val report = BoardCompatibilityEngine.evaluate(preview, currentBoardProfile())
        if (report.level == CompatibilityLevel.INCOMPATIBLE) {
            repository.setImportError(
                "El proyecto no es compatible con la placa ${preview.boardProfile}."
            )
            return false
        }
        val remappedById = report.remapped.associate { it.componentId to it.toPins }
        val components = preview.components.map { component ->
            remappedById[component.id]?.let { component.copy(pins = it) } ?: component
        }
        repository.setDevice(currentDeviceId())
        val before = repository.configSnapshot()
        return try {
            when (mode) {
                ImportMode.REPLACE -> {
                    repository.setDesired(LocalDesiredConfig(components = components))
                    repository.setAlertRules(preview.alerts)
                    repository.setAutomationRules(preview.automations)
                    val calibration = preview.calibrations
                        .firstOrNull { it.deviceId == repository.state.value.deviceId }
                    if (calibration != null) {
                        repository.setSoilCalibration(calibration.dryRaw, calibration.wetRaw)
                    } else {
                        repository.clearSoilCalibration()
                    }
                }
                ImportMode.COPY -> {
                    val existing = repository.state.value
                    repository.setDesired(
                        LocalDesiredConfig(
                            components = mergeComponents(existing.desiredConfig.components, components)
                        )
                    )
                    repository.setAlertRules(mergeAlerts(existing.alertRules, preview.alerts))
                    repository.setAutomationRules(
                        mergeAutomations(existing.automationRules, preview.automations)
                    )
                }
            }
            repository.clearImport()
            true
        } catch (cancelled: CancellationException) {
            repository.restore(before)
            throw cancelled
        } catch (failure: Exception) {
            repository.restore(before)
            repository.setImportError(failure.message ?: "No se pudo aplicar el proyecto.")
            false
        }
    }

    private fun mergeComponents(
        current: List<LocalDesiredComponent>,
        incoming: List<LocalDesiredComponent>
    ): List<LocalDesiredComponent> {
        val usedIds = current.map { it.id }.toMutableSet()
        val merged = current.toMutableList()
        for (component in incoming) {
            val id = if (usedIds.contains(component.id)) {
                var candidate = "imp_${component.id}"
                var counter = 1
                while (usedIds.contains(candidate)) {
                    candidate = "imp_${counter}_${component.id}"
                    counter++
                }
                candidate
            } else {
                component.id
            }
            usedIds.add(id)
            merged.add(component.copy(id = id))
        }
        return merged
    }

    private fun uniqueId(base: String, used: MutableSet<String>): String {
        if (used.add(base)) return base
        var candidate = "imp_$base"
        var counter = 1
        while (!used.add(candidate)) {
            candidate = "imp_${counter}_$base"
            counter++
        }
        return candidate
    }

    private fun mergeAlerts(current: List<AlertRule>, incoming: List<AlertRule>): List<AlertRule> {
        val used = current.map { it.id }.toMutableSet()
        return current + incoming.map { it.copy(id = uniqueId(it.id, used)) }
    }

    private fun mergeAutomations(
        current: List<AutomationRule>,
        incoming: List<AutomationRule>
    ): List<AutomationRule> {
        val used = current.map { it.id }.toMutableSet()
        return current + incoming.map { it.copy(id = uniqueId(it.id, used)) }
    }

    companion object {
        const val DEFAULT_PROJECT_NAME = "proyecto-compi"
        const val DEFAULT_PROJECT_ID = "local"
        const val DEFAULT_BOARD_PROFILE = "esp32-legacy-v1"

        fun factory(
            context: Context,
            session: CompiLabSession?,
            bleManager: BleManager?
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repository = ComponentStatusRepository(DataStoreComponentStatusStorage(context))
                val client = LocalLabClient(context)
                val wifiAuthorized = {
                    session != null && session.transport == LabTransport.WIFI_LAB &&
                        session.wifiReady && LocalLabClient.validKey(session.key) &&
                        session.connectionState == LabConnectionState.AUTHORIZED
                }
                val bleAuthorized = {
                    session != null && session.transport == LabTransport.BLUETOOTH &&
                        bleManager?.connectionState?.value == BleConnectionState.Ready &&
                        bleManager.securityState.value == BleSecurityState.Authorized
                }
                val authorized = { wifiAuthorized() || bleAuthorized() }
                val deviceId = {
                    bleManager?.publicDeviceId?.value
                        ?: bleManager?.connectedAddress?.value
                        ?: "esp32-lab"
                }
                // The same mutex used by RGB operations prevents interleaving
                // telemetry/status requests with acknowledged hardware commands.
                // Discard a delayed answer if the control session has changed.
                val fetch = suspend {
                    when {
                        bleAuthorized() -> bleManager?.readComponentSnapshot(
                            BleSnapshotAssembler.Kind.STATUS
                        )?.takeIf { bleAuthorized() }
                        wifiAuthorized() -> {
                            val activeSession = session!!
                            val key = activeSession.key
                            val revision = activeSession.revision
                            val payload = activeSession.serialized {
                                if (!wifiAuthorized()) null
                                else client.componentStatus(key).toString()
                            }
                            payload?.takeIf { wifiAuthorized() &&
                                activeSession.revision == revision }
                        }
                        else -> null
                    }
                }
                val fetchTelemetry = suspend {
                    when {
                        bleAuthorized() -> bleManager?.readComponentSnapshot(
                            BleSnapshotAssembler.Kind.TELEMETRY
                        )?.takeIf { bleAuthorized() }
                        wifiAuthorized() -> {
                            val activeSession = session!!
                            val key = activeSession.key
                            val revision = activeSession.revision
                            val payload = activeSession.serialized {
                                if (!wifiAuthorized()) null
                                else client.componentTelemetry(key).toString()
                            }
                            payload?.takeIf { wifiAuthorized() &&
                                activeSession.revision == revision }
                        }
                        else -> null
                    }
                }
                // Hardware automation actions reuse the EXISTING command path:
                // the authorized Wi-Fi client (with readback) or the BLE queue.
                // No second transport is created here.
                val automationSink = AutomationActionSink { action ->
                    when (action) {
                        is AutomationAction.SetRgb -> when {
                            wifiAuthorized() && session != null -> session.serialized {
                                if (!wifiAuthorized()) false else {
                                    client.setRgbConfirmed(
                                        session.key, action.red, action.green,
                                        action.blue, action.brightness
                                    )
                                    wifiAuthorized()
                                }
                            }
                            bleAuthorized() && bleManager != null -> {
                                val revision = bleManager.stateRevision.value
                                bleManager.writeCommand(RgbDeviceCommand.set(
                                    action.red, action.green, action.blue, action.brightness
                                ))
                                // BLE queue acceptance is not execution. Wait for a NEW
                                // authenticated controller STATE notification.
                                withTimeoutOrNull(4_000L) {
                                    bleManager.stateRevision.first { it > revision &&
                                        bleAuthorized() && RgbReadback.confirmsSet(
                                            action.red, action.green, action.blue,
                                            action.brightness, bleManager.rgbState.value
                                        )
                                    }
                                    true
                                } == true
                            }
                            else -> false
                        }
                        is AutomationAction.UpdateOled -> {
                            if (MediaControlProtocol.request("OLD", "auto", "TEXT", action.text) == null)
                                false
                            else when {
                                wifiAuthorized() && session != null -> session.serialized {
                                    if (!wifiAuthorized()) false else {
                                        client.showOled(session.key, "text", action.text)
                                        val state = client.oledStatus(session.key)
                                        wifiAuthorized() && state.ready && state.scene == "text"
                                    }
                                }
                                bleAuthorized() && bleManager != null -> {
                                    val reply = bleManager.mediaCommand("OLD", "TEXT", action.text)
                                    bleAuthorized() && reply.result == "OK" &&
                                        reply.oled?.let { it.ready && it.scene == "text" } == true
                                }
                                else -> false
                            }
                        }
                        is AutomationAction.Notify -> true
                    }
                }
                val readProjectText: suspend (String) -> String? = { location ->
                    runCatching {
                        context.contentResolver.openInputStream(android.net.Uri.parse(location))
                            ?.use { it.readBytes().toString(Charsets.UTF_8) }
                    }.getOrNull()
                }
                val writeProjectText: suspend (String, String) -> Boolean = { location, text ->
                    runCatching {
                        context.contentResolver.openOutputStream(android.net.Uri.parse(location), "wt")
                            ?.use {
                                it.write(text.toByteArray(Charsets.UTF_8))
                                true
                            } ?: false
                    }.getOrDefault(false)
                }
                return ComponentDashboardViewModel(
                    repository, authorized, deviceId, fetch, fetchTelemetry, automationSink,
                    readProjectText, writeProjectText
                ) as T
            }
        }
    }
}
