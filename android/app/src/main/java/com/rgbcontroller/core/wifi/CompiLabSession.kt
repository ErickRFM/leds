package com.rgbcontroller.core.wifi

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rgbcontroller.core.model.RgbDeviceState
import kotlinx.coroutines.sync.Mutex

/**
 * One app-scoped, volatile session for LED, Studio and Hardware Lab.
 * Keys never enter DataStore, saved-instance state, logs or exported projects.
 * This is a LAB transport, not owner enrollment or cryptographic pairing.
 */
enum class LabTransport { BLUETOOTH, WIFI_LAB }

class CompiLabSession {
    private val networkMutex = Mutex()

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
    var message by mutableStateOf("Conecta a ESP32-Control-Lab para usar Wi-Fi.")
        private set
    var revision by mutableIntStateOf(0)
        private set

    fun setKey(value: String) {
        val sanitized = value.trim().take(16)
        if (sanitized != key) {
            key = sanitized
            invalidate("La clave cambió; vuelve a sincronizar el ESP32.")
        }
    }

    fun selectTransport(next: LabTransport) {
        if (transport != next) {
            transport = next
            invalidate("Transporte cambiado; el último estado Wi-Fi requiere sincronización.")
        }
    }

    /** Increments revision so delayed responses cannot revive an invalid session. */
    fun invalidate(reason: String = "Sesión Wi-Fi sin confirmar.") {
        revision += 1
        wifiReady = false
        rgbState = null
        message = reason
    }

    fun beginVerification(): Pair<Int, String> {
        invalidate("Verificando la conexión con ESP32-Control-Lab…")
        return revision to key
    }

    fun verified(epoch: Int, verifiedKey: String, state: RgbDeviceState): Boolean {
        if (epoch != revision || verifiedKey != key || transport != LabTransport.WIFI_LAB) return false
        wifiReady = true
        rgbState = state
        message = "Estado RGB leído del ESP32; conexión de laboratorio confirmada."
        return true
    }

    fun commandReadback(epoch: Int, verifiedKey: String, state: RgbDeviceState): Boolean {
        if (epoch != revision || verifiedKey != key || transport != LabTransport.WIFI_LAB ||
            !wifiReady) return false
        rgbState = state
        message = "Comando aplicado y estado leído del ESP32."
        return true
    }

    fun failed(epoch: Int, details: String) {
        if (epoch == revision && transport == LabTransport.WIFI_LAB) {
            invalidate(details)
        }
    }

    fun clear() {
        key = ""
        transport = LabTransport.BLUETOOTH
        invalidate("Sesión de laboratorio cerrada.")
    }
}
