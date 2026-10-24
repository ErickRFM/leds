package com.rgbcontroller.feature.modules

import androidx.compose.runtime.*
import com.rgbcontroller.core.ble.*
import com.rgbcontroller.core.wifi.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** UI lifetime state. Recreated whenever transport, authorization or session changes. */
@Stable
class HardwareMediaControls(
    private val session: CompiLabSession,
    private val ble: BleManager,
    private val wifi: LocalLabClient,
    private val epoch: Int,
    private val transport: LabTransport
) {
    var audio by mutableStateOf<AudioHardwareState?>(null)
        private set
    var oled by mutableStateOf<OledHardwareState?>(null)
        private set
    var audioMessage by mutableStateOf<String?>(null)
        private set
    var oledMessage by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    private fun verifySession() {
        check(session.revision == epoch && session.transport == transport) { "La conexión cambió; vuelve a consultar." }
        check(if (transport == LabTransport.BLUETOOTH)
            ble.connectionState.value == BleConnectionState.Ready && ble.securityState.value == BleSecurityState.Authorized
        else session.wifiReady && session.connectionState == LabConnectionState.AUTHORIZED) { "Conecta y autoriza el ESP32." }
    }

    suspend fun command(module: String, verb: String, value: String? = null) {
        val manual = verb != "GET"
        if (manual && busy) return
        if (manual) busy = true
        try {
            session.userOperation(if (module == "AUD") "Audio $verb" else "OLED $verb") {
                verifySession()
                if (transport == LabTransport.BLUETOOTH) {
                    val reply = ble.mediaCommand(module, verb, value)
                    verifySession()
                    if (module == "AUD") {
                        audio = reply.audio?.let {
                            if (reply.result == "ACCEPTED") it.copy(playback = "UNKNOWN", track = -1) else it
                        }
                    } else oled = reply.oled
                } else {
                    val key = session.key
                    if (module == "AUD") {
                        if (manual) wifi.audioCommand(key, verb, value)
                        val reported = wifi.audioStatus(key)
                        verifySession()
                        audio = if (manual && verb != "VOICE") reported.copy(playback = "UNKNOWN", track = -1) else reported
                    } else {
                        if (manual) wifi.showOled(key, verb.lowercase(), requireNotNull(value))
                        val reported = wifi.oledStatus(key)
                        verifySession()
                        oled = reported
                    }
                }
            }
            if (manual) {
                if (module == "AUD") audioMessage = "ESP32 aceptó $verb; el estado se consulta al módulo."
                else oledMessage = "ESP32 confirmó el estado OLED."
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (module == "AUD") { audio = null; audioMessage = failure.message }
            else { oled = null; oledMessage = failure.message }
        } finally {
            if (manual) busy = false
        }
    }
}

@Composable
fun rememberHardwareMediaControls(
    session: CompiLabSession, ble: BleManager, wifi: LocalLabClient,
    authorized: Boolean, audioVisible: Boolean, oledVisible: Boolean
): HardwareMediaControls {
    val controls = remember(session.revision, session.transport, authorized) {
        HardwareMediaControls(session, ble, wifi, session.revision, session.transport)
    }
    LaunchedEffect(controls, authorized, audioVisible, oledVisible) {
        if (authorized) while (true) {
            if (audioVisible) controls.command("AUD", "GET")
            if (oledVisible) controls.command("OLD", "GET")
            delay(3_000)
        }
    }
    return controls
}
