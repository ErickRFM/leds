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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.ui.Alignment

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
    val bleSnapshot by bleManager.environmentalRgbSnapshot.collectAsState()
    val bleReply by bleManager.environmentalRgbMessage.collectAsState()
    val authorized = if (wifiMode) {
        session.connectionState == LabConnectionState.AUTHORIZED && session.wifiReady
    } else {
        bleConnection == BleConnectionState.Ready &&
            bleSecurity == BleSecurityState.Authorized
    }
    var wifiEnabled by remember { mutableStateOf<Boolean?>(null) }
    var wifiState by remember { mutableStateOf("DISABLED") }
    var wifiReason by remember { mutableStateOf("DISABLED") }
    var wifiBand by remember { mutableStateOf("Comfort") }
    var wifiRed by remember { mutableStateOf(0) }
    var wifiGreen by remember { mutableStateOf(0) }
    var wifiBlue by remember { mutableStateOf(0) }
    var wifiBrightness by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Esperando estado ambiental del ESP32.") }

    val enabled = if (wifiMode) wifiEnabled else bleEnabled
    val currentState = if (wifiMode) wifiState else (bleSnapshot?.state?.name ?: "DISABLED")
    val currentReason = if (wifiMode) wifiReason else (bleSnapshot?.reason ?: "DISABLED")
    val currentRed = if (wifiMode) wifiRed else (bleSnapshot?.red ?: 0)
    val currentGreen = if (wifiMode) wifiGreen else (bleSnapshot?.green ?: 0)
    val currentBlue = if (wifiMode) wifiBlue else (bleSnapshot?.blue ?: 0)
    val currentBand = if (wifiMode) wifiBand else (bleSnapshot?.band ?: "Comfort")

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
                            wifiState = status.optString("state", if (wifiEnabled == true) "ENABLED" else "DISABLED")
                            wifiReason = status.optString("reason", "OK")
                            wifiBand = status.optString("band", "Comfort")
                            wifiRed = status.optInt("red", 0)
                            wifiGreen = status.optInt("green", 0)
                            wifiBlue = status.optInt("blue", 0)
                            wifiBrightness = status.optInt("brightness", 80)
                            message = when (wifiState) {
                                "RUNNING" -> "ESP32 confirmó RGB ambiental activo ($wifiBand: R:$wifiRed, G:$wifiGreen, B:$wifiBlue, brillo:$wifiBrightness)."
                                "SUSPENDED" -> "Modo ambiental en espera: temperatura no disponible o desactualizada ($wifiReason)."
                                "CONFLICT" -> "Modo ambiental suspendido por conflicto ($wifiReason)."
                                "ERROR" -> "Error en módulo RGB del ESP32 ($wifiReason)."
                                else -> "ESP32 confirmó el modo manual."
                            }
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
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("RGB ambiental", style = MaterialTheme.typography.titleMedium)
                val badgeColor = when (currentState) {
                    "RUNNING" -> Color(0xFF36BA74)
                    "SUSPENDED" -> Color(0xFFF6B83F)
                    "CONFLICT" -> Color(0xFFFF7D14)
                    "ERROR" -> Color(0xFFE62323)
                    else -> Color.Gray
                }
                AssistChip(
                    onClick = {},
                    label = { Text(currentState, style = MaterialTheme.typography.labelSmall) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = badgeColor.copy(alpha = 0.2f), labelColor = badgeColor),
                    modifier = Modifier.testTag("environmental_rgb_status_chip")
                )
            }
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
            if (currentState == "RUNNING" && (currentRed > 0 || currentGreen > 0 || currentBlue > 0)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(24.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = Color(currentRed, currentGreen, currentBlue)
                    ) {}
                    Text(
                        "Color ambiental aplicado: R:$currentRed G:$currentGreen B:$currentBlue ($currentBand)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Text(
                "Temperatura: azul (<20°C), verde (20-26°C), amarillo (26-30°C), naranja (30-35°C) o rojo (≥35°C). " +
                    "Sin temperatura válida, el ESP32 no realiza cambios.",
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
