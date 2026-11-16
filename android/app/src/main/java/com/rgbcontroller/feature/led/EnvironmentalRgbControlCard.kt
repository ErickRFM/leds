package com.rgbcontroller.feature.led

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
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

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.rgbcontroller.core.ui.CompiTokens

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
    var wifiEnabled by remember(transport, session.revision) { mutableStateOf<Boolean?>(null) }
    var wifiState by remember(transport, session.revision) { mutableStateOf("UNKNOWN") }
    var wifiReason by remember(transport, session.revision) { mutableStateOf("AWAITING_STATUS") }
    var wifiBand by remember(transport, session.revision) { mutableStateOf("Comfort") }
    var wifiRed by remember(transport, session.revision) { mutableStateOf(0) }
    var wifiGreen by remember(transport, session.revision) { mutableStateOf(0) }
    var wifiBlue by remember(transport, session.revision) { mutableStateOf(0) }
    var wifiBrightness by remember(transport, session.revision) { mutableStateOf(0) }
    var busy by remember(transport, session.revision) { mutableStateOf(false) }
    var message by remember(transport, session.revision) { mutableStateOf("Esperando estado ambiental del ESP32.") }

    val enabled = if (wifiMode) wifiEnabled else bleEnabled
    val currentState = if (!authorized) "OFFLINE" else if (wifiMode) wifiState else (bleSnapshot?.state?.name ?: "UNKNOWN")
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
                            wifiState = status.optString("state", "UNKNOWN")
                            wifiReason = status.optString("reason", "UNKNOWN")
                            wifiBand = status.optString("band", "Comfort")
                            wifiRed = status.optInt("red", 0)
                            wifiGreen = status.optInt("green", 0)
                            wifiBlue = status.optInt("blue", 0)
                            wifiBrightness = status.optInt("brightness", 0)
                            message = when (wifiState) {
                                "RUNNING" -> "ESP32 confirmó RGB ambiental activo ($wifiBand: R:$wifiRed, G:$wifiGreen, B:$wifiBlue, brillo:$wifiBrightness)."
                                "SUSPENDED" -> "Modo ambiental en espera: temperatura no disponible o desactualizada ($wifiReason)."
                                "CONFLICT" -> "Modo ambiental suspendido por conflicto ($wifiReason)."
                                "ERROR" -> "Error en módulo RGB del ESP32 ($wifiReason)."
                                "DISABLED" -> "ESP32 confirmó el modo ambiental desactivado."
                                "UNKNOWN" -> "El ESP32 informó un estado que esta app no reconoce ($wifiReason)."
                                else -> "Estado ambiental aún sin confirmar ($wifiReason)."
                            }
                        }
                    } else if (!bleManager.requestEnvironmentalRgb(BleEnvironmentalProtocol.Action.GET)) {
                        message = "Bluetooth aún no autoriza consultas ambientales."
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (wifiMode) { wifiEnabled = null; wifiState = "UNKNOWN" }
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
                EnvironmentalStateBadge(currentState, Modifier.testTag("environmental_rgb_status_chip"))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (color in listOf(
                    Color(0xFF0000FF), Color(0xFF00FF00),
                    Color(0xFFFFFF00), Color(0xFFFF6E00), Color(0xFFFF0000)
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
                        "Color ordenado al PWM: R:$currentRed G:$currentGreen B:$currentBlue ($currentBand)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Text(
                when (currentBand.lowercase()) {
                    "cold", "frio", "frío" -> "Frío · menos de 20 °C"
                    "comfort", "confort" -> "Confort · 20 a 26 °C"
                    "warm", "calido", "cálido" -> "Cálido · 26 a 30 °C"
                    "hot", "calor" -> "Calor · 30 a 35 °C"
                    "extreme", "extremo" -> "Extremo · 35 °C o más"
                    else -> currentBand
                },
                style = MaterialTheme.typography.bodyMedium
            )
            if (currentState != "RUNNING") {
                Text(
                    when {
                        !authorized -> "Dispositivo desconectado"
                        currentState == "SUSPENDED" -> "Esperando una lectura de temperatura vigente"
                        currentState == "DISABLED" -> "Modo ambiental apagado"
                        currentState == "CONFLICT" ->
                            "Otro control (manual, script, automatización o experimento) tiene el RGB; libéralo para activar el modo ambiental."
                        currentState == "ERROR" ->
                            "El ESP32 reportó un error en el módulo RGB (${currentReason}); revisa el cableado antes de reintentar."
                        currentState == "UNKNOWN" ->
                            "El firmware envió un estado que esta app no reconoce (${currentReason})."
                        else -> "Esperando la confirmación del ESP32."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
                                wifiState = "UNKNOWN"
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

/**
 * Read-only firmware state: an interactive chip would let TalkBack announce a
 * control that does nothing. The Spanish label and icon carry the meaning, so
 * the colour is a reinforcement rather than the only signal, and every colour
 * comes from the active scheme to stay legible in light and deep-black themes.
 * Internal because the firmware only reports these states with a board
 * attached, so an instrumented test composes each one directly instead of the
 * app inventing telemetry.
 */
@Composable
internal fun EnvironmentalStateBadge(state: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val style = when (state) {
        "RUNNING" -> BadgeStyle(scheme.tertiaryContainer, scheme.onTertiaryContainer,
            Icons.Default.CheckCircle, "Activo")
        "SUSPENDED" -> BadgeStyle(scheme.secondaryContainer, scheme.onSecondaryContainer,
            Icons.Default.Sync, "En espera")
        "CONFLICT" -> BadgeStyle(scheme.secondaryContainer, scheme.onSecondaryContainer,
            Icons.Default.Shield, "En conflicto")
        "ERROR" -> BadgeStyle(scheme.errorContainer, scheme.onErrorContainer,
            Icons.Default.ErrorOutline, "Error")
        "OFFLINE" -> BadgeStyle(scheme.errorContainer, scheme.onErrorContainer,
            Icons.Default.CloudOff, "Sin conexión")
        "DISABLED" -> BadgeStyle(scheme.surfaceVariant, scheme.onSurfaceVariant,
            Icons.Default.Info, "Desactivado")
        else -> BadgeStyle(scheme.surfaceVariant, scheme.onSurfaceVariant,
            Icons.Default.Info, "Sin confirmar")
    }
    Surface(
        color = style.container,
        shape = CompiTokens.Shapes.Small,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = CompiTokens.Spacing.xs, vertical = CompiTokens.Spacing.xxs
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CompiTokens.Spacing.xxs)
        ) {
            Icon(
                imageVector = style.icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = style.content
            )
            Text(
                text = style.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = style.content
            )
        }
    }
}

private data class BadgeStyle(
    val container: Color,
    val content: Color,
    val icon: ImageVector,
    val label: String
)
