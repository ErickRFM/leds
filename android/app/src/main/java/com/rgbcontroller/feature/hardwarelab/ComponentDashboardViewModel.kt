package com.rgbcontroller.feature.hardwarelab

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.core.components.status.ComponentDashboardState
import com.rgbcontroller.core.components.status.ComponentStatusRepository
import com.rgbcontroller.core.components.status.LocalDesiredConfig
import com.rgbcontroller.core.data.DataStoreComponentStatusStorage
import com.rgbcontroller.core.wifi.CompiLabSession
import com.rgbcontroller.core.wifi.LabConnectionState
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
    private val fetchTelemetry: suspend () -> String? = { null }
) : ViewModel() {

    val uiState: StateFlow<ComponentDashboardState> = repository.state

    suspend fun loadPersisted() = repository.loadPersisted()

    /** Re-evaluate connection and refresh authoritative status when authorized. */
    suspend fun onConnectionChanged() {
        repository.setDevice(currentDeviceId())
        val connected = isLinkAuthorized()
        repository.setConnected(connected)
        if (connected) refresh()
    }

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
            repository.reportError(failure.message ?: "Error de comunicación con el ESP32.")
            return
        }
        if (raw == null) {
            repository.reportError("El firmware no respondió con un estado de componentes.")
            return
        }
        repository.ingest(raw)
        // Telemetry is secondary: a failure must never discard confirmed status,
        // and absent readings must never be invented.
        val telemetry = try {
            fetchTelemetry()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            repository.reportError(failure.message ?: "Error al leer la telemetría del ESP32.")
            return
        }
        if (telemetry != null) {
            repository.ingestTelemetry(telemetry)
        }
    }

    suspend fun setLocalDesired(config: LocalDesiredConfig) = repository.setDesired(config)

    companion object {
        fun factory(
            context: Context,
            session: CompiLabSession?,
            bleManager: BleManager?
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repository = ComponentStatusRepository(DataStoreComponentStatusStorage(context))
                val client = LocalLabClient(context)
                val authorized = {
                    session != null && LocalLabClient.validKey(session.key) &&
                        session.connectionState == LabConnectionState.AUTHORIZED
                }
                val deviceId = {
                    bleManager?.publicDeviceId?.value
                        ?: bleManager?.connectedAddress?.value
                        ?: "esp32-lab"
                }
                val fetch = suspend {
                    if (authorized()) client.componentStatus(session!!.key).toString() else null
                }
                val fetchTelemetry = suspend {
                    if (authorized()) client.componentTelemetry(session!!.key).toString() else null
                }
                return ComponentDashboardViewModel(
                    repository, authorized, deviceId, fetch, fetchTelemetry
                ) as T
            }
        }
    }
}
