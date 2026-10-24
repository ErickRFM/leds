package com.rgbcontroller.feature.led

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.ble.BleConnectionState
import com.rgbcontroller.core.ble.BleEnvironmentalProtocol
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.core.ble.BleSecurityState
import com.rgbcontroller.core.wifi.CompiLabSession
import com.rgbcontroller.core.wifi.LabConnectionState
import com.rgbcontroller.core.wifi.LabTransport
import com.rgbcontroller.core.wifi.LocalLabClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Reuses the same firmware environmental state machine on BLE and Wi-Fi.
 * The UI NEVER treats a GATT write callback as environmental-mode acceptance.
 */
@Composable
fun EnvironmentalRgbControlCard(
    session: CompiLabSession, client: LocalLabClient, bleManager: BleManager
) {
    val scope = rememberCoroutineScope()
    val transport = session.transport
    val wifiMode = transport == LabTransport.WIFI_LAB
    val bleConnection by bleManager.connectionState.collectAsState()
    val bleSecurity by bleManager.securityState.collectAsState()
    val bleEnabled by bleManager.environmentalRgbEnabled.collectAsState()
    val bleReply by bleManager.environmentalRgbMessage.collectAsState()
    val authorized = if (wifiMode) {
        session.connectionState == LabConnectionState.AUTHORIZED && session.wifiReady
    } else {
        bleConnection == BleConnectionState.Ready &&
            bleSecurity == BleSecurityState.Authorized
    }
    var wifiEnabled by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Esperando estado ambiental del ESP32.") }
    val enabled = if (wifiMode) wifiEnabled else bleEnabled

    LaunchedEffect(authorized, wifiMode, session.revision) {
        if (!authorized) {
            wifiEnabled = null
            message = if (wifiMode) "Conecta al Wi-Fi autorizado del ESP32."
                else "Conecta y autoriza al propietario Bluetooth para activar RGB ambiental."
            return@LaunchedEffect
        }
        while (true) {
            try {
                if (!busy) {
                    if (wifiMode) {
                        val version = session.revision
                        val key = session.key
                        val status = session.serialized { client.environmentalRgbStatus(key) }
                        if (authorized && session.revision == version && !busy) {
                            wifiEnabled = status.optBoolean("enabled")
                            message = if (wifiEnabled == true)
                                "ESP32 confirmó el modo ambiental activo."
                            else "ESP32 confirmó el modo manual."
                        }
                    } else if (!bleManager.requestEnvironmentalRgb(BleEnvironmentalProtocol.Action.GET)) {
                        message = "Bluetooth aún no autoriza consultas ambientales."
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (wifiMode) wifiEnabled = null
                message = "No se confirmó el modo ambiental: ${failure.message}"
            }
            delay(3_500L)
        }
    }

    Card(Modifier.fillMaxWidth().testTag("environmental_rgb_control")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("RGB ambiental", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (color in listOf(
                    Color(0xFF3478ED), Color(0xFF36BA74),
                    Color(0xFFF6B83F), Color(0xFFFF7D14), Color(0xFFE62323)
                )) {
                    Surface(
                        modifier = Modifier.weight(1f).height(12.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = color
                    ) {}
                }
            }
            Text(
                "Temperatura: azul, verde, ámbar, naranja o rojo. La humedad suaviza el tono " +
                    "y la LDR calibrada regula el brillo. Sin temperatura válida, " +
                    "el ESP32 mantiene el último color.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                when {
                    !authorized -> if (wifiMode) message
                        else "Necesitas autorización BLE de propietario (cifrado solo no basta)."
                    !wifiMode -> bleReply
                    else -> message
                },
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                enabled = authorized && !busy,
                modifier = Modifier.fillMaxWidth().testTag("environmental_rgb_toggle"),
                onClick = {
                    scope.launch {
                        busy = true
                        try {
                            check(authorized) { "Perdiste la autorización con el ESP32." }
                            if (wifiMode) {
                                val version = session.revision
                                val key = session.key
                                session.serialized {
                                    if (enabled == true) client.stopEnvironmentalRgb(key)
                                    else client.startEnvironmentalRgb(key)
                                }
                                check(authorized && session.revision == version) {
                                    "Cambió la sesión Wi-Fi; vuelve a verificar."
                                }
                                wifiEnabled = null  // await read-back, never assume success
                                message = "Esperando la confirmación de estado del ESP32…"
                            } else {
                                val action = if (enabled == true)
                                    BleEnvironmentalProtocol.Action.STOP
                                else BleEnvironmentalProtocol.Action.START
                                check(bleManager.requestEnvironmentalRgb(action)) {
                                    "El ESP32 no aceptó encolar el comando BLE."
                                }
                                // BLE mode only changes with ENV,ON / ENV,OFF notification.
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            message = "No se confirmó el cambio: ${failure.message}"
                        } finally {
                            busy = false
                        }
                    }
                }
            ) {
                Text(if (enabled == true) "Detener modo ambiental" else "Activar modo ambiental")
            }
        }
    }
}
