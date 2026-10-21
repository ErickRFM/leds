package com.rgbcontroller.core.wifi

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rgbcontroller.core.model.RgbDeviceState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.CancellationException

/**
 * One app-scoped, volatile session for LED, Studio and Hardware Lab.
 * Keys never enter DataStore, saved-instance state, logs or exported projects.
 * This is a LAB transport, not owner enrollment or cryptographic pairing.
 */
enum class LabTransport { BLUETOOTH, WIFI_LAB }

enum class LabOperationPhase { IDLE, QUEUED, RUNNING, CONFIRMED, FAILED, CANCELLED }
data class LabOperationStatus(val name: String, val phase: LabOperationPhase, val details: String = "")

enum class LabConnectionState {
    DISCONNECTED,
    SOFTAP_DETECTED,
    LOCAL_ROUTE_AVAILABLE,
    CONTROL_KEY_REQUIRED,
    VERIFYING,
    AUTHORIZED,
    AUTH_FAILED,
    NETWORK_ERROR,
    SESSION_EXPIRED
}

class CompiLabSession {
    private val networkMutex = Mutex()
    var operationStatus by mutableStateOf(LabOperationStatus("", LabOperationPhase.IDLE))
        private set
    private var latestOperationId = 0L

    var connectionState by mutableStateOf(LabConnectionState.DISCONNECTED)
        private set

    var isNetworkRouteAvailable by mutableStateOf(false)
        private set

    fun updateNetworkRouteAvailable(available: Boolean) {
        if (isNetworkRouteAvailable != available) {
            isNetworkRouteAvailable = available
            if (!available && wifiReady) {
                invalidate("Se perdió la ruta Wi-Fi; vuelve a verificar el ESP32.")
            } else {
                reevaluateConnectionState()
            }
        }
    }

    private fun reevaluateConnectionState() {
        if (transport != LabTransport.WIFI_LAB) {
            connectionState = LabConnectionState.DISCONNECTED
            return
        }
        if (!isNetworkRouteAvailable) {
            connectionState = LabConnectionState.SOFTAP_DETECTED
            message = "Red ESP32 no detectada o sin ruta 192.168.4.1."
            return
        }
        if (!LabAuthPolicy.validKey(key)) {
            connectionState = LabConnectionState.CONTROL_KEY_REQUIRED
            message = "Red ESP32 detectada · Falta autorizar el control."
            return
        }
        if (!wifiReady) {
            connectionState = LabConnectionState.LOCAL_ROUTE_AVAILABLE
            message = "Clave lista; presiona Conectar Wi-Fi."
        }
    }

    /**
     * User initiated commands are queued with the same mutex as background
     * readbacks, so a write and its confirmation cannot overlap a second HTTP
     * operation. Never interpret HTTP acceptance as a physical LED measurement.
     */
    suspend fun <T> userOperation(name: String, block: suspend () -> T): T {
        val id = ++latestOperationId
        operationStatus = LabOperationStatus(name, LabOperationPhase.QUEUED)
        return try {
            val value = serialized {
                if (id == latestOperationId) {
                    operationStatus = LabOperationStatus(name, LabOperationPhase.RUNNING)
                }
                block()
            }
            if (id == latestOperationId) {
                operationStatus = LabOperationStatus(name, LabOperationPhase.CONFIRMED,
                    "El firmware respondió; verifica el hardware físico.")
            }
            value
        } catch (cancelled: CancellationException) {
            if (id == latestOperationId) {
                operationStatus = LabOperationStatus(name, LabOperationPhase.CANCELLED)
            }
            throw cancelled
        } catch (failure: Exception) {
            if (id == latestOperationId) {
                operationStatus = LabOperationStatus(name, LabOperationPhase.FAILED,
                    failure.message ?: "El ESP32 no respondió.")
            }
            throw failure
        }
    }

    /** Serialize authenticated SoftAP operations across LED, Studio and Lab. */
    suspend fun <T> serialized(block: suspend () -> T): T {
        networkMutex.lock()
        try { return block() }
        finally { networkMutex.unlock() }
    }

    var transport by mutableStateOf(LabTransport.BLUETOOTH)
        private set
    var key by mutableStateOf("")
        private set
    var wifiReady by mutableStateOf(false)
        private set
    var rgbState by mutableStateOf<RgbDeviceState?>(null)
        private set
    var stateRevision by mutableStateOf(0L)
        private set
    var message by mutableStateOf("Conecta a ESP32-Control-Lab para usar Wi-Fi.")
        private set
    var revision by mutableIntStateOf(0)
        private set

    fun updateKey(value: String) {
        val sanitized = value.trim().take(16)
        if (sanitized != key) {
            key = sanitized
            reevaluateConnectionState()
            invalidate("La clave cambió; vuelve a sincronizar el ESP32.")
        }
    }

    fun selectTransport(next: LabTransport) {
        if (transport != next) {
            transport = next
            reevaluateConnectionState()
            invalidate("Transporte cambiado; el último estado Wi-Fi requiere sincronización.")
        }
    }

    /** Increments revision so delayed responses cannot revive an invalid session. */
    fun invalidate(reason: String = "Sesión Wi-Fi sin confirmar.", preserveErrorState: Boolean = false) {
        revision += 1
        wifiReady = false
        rgbState = null
        message = reason
        if (transport == LabTransport.WIFI_LAB && !preserveErrorState) {
            reevaluateConnectionState()
        }
    }

    fun beginVerification(): Pair<Int, String> {
        connectionState = LabConnectionState.VERIFYING
        invalidate("Verificando la conexión con ESP32-Control-Lab…", preserveErrorState = true)
        return revision to key
    }

    fun verified(epoch: Int, verifiedKey: String, state: RgbDeviceState): Boolean {
        if (epoch != revision || verifiedKey != key || transport != LabTransport.WIFI_LAB ||
            !isNetworkRouteAvailable) return false
        wifiReady = true
        rgbState = state
        stateRevision += 1
        connectionState = LabConnectionState.AUTHORIZED
        message = "ESP32 autorizado · Estado RGB sincronizado."
        return true
    }

    fun commandReadback(epoch: Int, verifiedKey: String, state: RgbDeviceState): Boolean {
        if (epoch != revision || verifiedKey != key || transport != LabTransport.WIFI_LAB ||
            !wifiReady || !isNetworkRouteAvailable) return false
        rgbState = state
        stateRevision += 1
        connectionState = LabConnectionState.AUTHORIZED
        message = "Comando aplicado y estado leído del ESP32."
        return true
    }

    fun failed(epoch: Int, error: Any) {
        if (epoch == revision && transport == LabTransport.WIFI_LAB) {
            val details = when (error) {
                is LabHttpException -> error.message
                is Throwable -> error.message ?: "Error de comunicación Wi-Fi"
                else -> error.toString()
            }
            val targetState = when (error) {
                is LabHttpException.Unauthorized, is LabHttpException.Forbidden -> LabConnectionState.AUTH_FAILED
                is LabHttpException.Conflict, is LabHttpException.BadRequest -> {
                    // Operational conflicts or bad payloads do NOT invalidate an authorized session.
                    message = details
                    return
                }
                is LabHttpException.RateLimited, is LabHttpException.ServerError,
                is LabHttpException.NetworkUnavailable, is LabHttpException.ProtocolError -> LabConnectionState.NETWORK_ERROR
                else -> LabConnectionState.NETWORK_ERROR
            }
            invalidate(details, preserveErrorState = true)
            connectionState = targetState
        }
    }

    fun clear() {
        latestOperationId += 1
        operationStatus = LabOperationStatus("", LabOperationPhase.IDLE)
        key = ""
        transport = LabTransport.BLUETOOTH
        isNetworkRouteAvailable = false
        connectionState = LabConnectionState.DISCONNECTED
        invalidate("Sesión de laboratorio cerrada.")
    }
}
