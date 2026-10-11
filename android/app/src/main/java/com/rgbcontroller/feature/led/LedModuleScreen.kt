package com.rgbcontroller.feature.led

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.ble.BleConnectionState
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.core.data.PreferencesRepository
import com.rgbcontroller.core.model.RgbControlLink
import com.rgbcontroller.core.model.RgbDeviceState
import com.rgbcontroller.core.wifi.LocalLabClient
import com.rgbcontroller.feature.control.ControlScreen
import com.rgbcontroller.feature.effects.EffectsScreen
import com.rgbcontroller.feature.presets.PresetsScreen
import com.rgbcontroller.core.ui.SectionHeader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

private enum class ConnectionMode { BLUETOOTH, WIFI_LAB }
private enum class LedPage { CONTROL, COLORS, EFFECTS }

/** One LED feature with equivalent commands; Wi-Fi remains an explicitly gated debug lab. */
@Composable
fun LedModuleScreen(
    bleManager: BleManager,
    preferencesRepository: PreferencesRepository,
    onOpenDevices: () -> Unit
) {
    val context = LocalContext.current
    val isDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val wifiClient = remember(context) { LocalLabClient(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val bleConnection by bleManager.connectionState.collectAsState()
    val bleState by bleManager.rgbState.collectAsState()
    var mode by remember { mutableStateOf(ConnectionMode.BLUETOOTH) }
    var page by remember { mutableStateOf(LedPage.CONTROL) }
    var wifiSecret by remember { mutableStateOf("") } // Memory only; never DataStore or logs.
    var wifiReady by remember { mutableStateOf(false) }
    var wifiBusy by remember { mutableStateOf(false) }
    var wifiState by remember { mutableStateOf<RgbDeviceState?>(null) }
    var wifiMessage by remember { mutableStateOf("Conecta al SoftAP ESP32-Control-Lab para usar Wi-Fi.") }

    // A separate queue for each connection epoch prevents replay after switching transport.
    val wifiCommands = remember(mode, wifiReady) { Channel<String>(Channel.UNLIMITED) }
    LaunchedEffect(mode, wifiReady, wifiCommands) {
        if (mode != ConnectionMode.WIFI_LAB || !wifiReady) return@LaunchedEffect
        val key = wifiSecret
        try {
            // Coalesce superseded slider SET commands, never discard ON/OFF or FX.
            var pending: String? = null
            while (true) {
                var command = pending ?: wifiCommands.receive()
                pending = null
                if (command.startsWith("SET,")) {
                    while (true) {
                        val next = wifiCommands.tryReceive().getOrNull() ?: break
                        if (next.startsWith("SET,")) command = next
                        else {
                            pending = next
                            break
                        }
                    }
                }
                wifiClient.rgb(key, command)
                val response = wifiClient.status(key)
                val parsed = RgbDeviceState.parse(response.optString("rgb"))
                    ?: error("Respuesta RGB inválida; vuelve a conectar.")
                if (command.startsWith("SET,")) {
                    val values = command.split(',').drop(1).map { it.toInt() }
                    check(com.rgbcontroller.core.model.RgbReadback.confirmsSet(
                        values[0], values[1], values[2], values[3], parsed
                    )) { "RGB no coincide con lo enviado; reconecta y comprueba la placa." }
                }
                wifiState = parsed
                wifiMessage = "Comando aplicado y estado leído del ESP32."
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            wifiReady = false
            wifiState = null
            wifiMessage = error.message ?: "Se perdió la conexión Wi-Fi."
        } finally {
            wifiCommands.cancel()
        }
    }

    val isWifi = mode == ConnectionMode.WIFI_LAB
    val connected = if (isWifi) wifiReady else bleConnection == BleConnectionState.Ready
    val state = if (isWifi) wifiState else bleState
    val link = RgbControlLink(
        transport = if (isWifi) "WIFI_LAB" else "BLUETOOTH",
        ready = connected,
        state = state,
        sendCommand = { command ->
            if (isWifi) {
                if (wifiReady) wifiCommands.trySend(command)
            } else if (bleConnection == BleConnectionState.Ready) {
                bleManager.writeCommand(command)
            }
        }
    )

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
            SectionHeader("LED RGB", "Un solo control, dos formas de conectarte")
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = !isWifi,
                    onClick = {
                        mode = ConnectionMode.BLUETOOTH
                        wifiReady = false
                        wifiState = null
                        wifiSecret = ""
                    },
                    label = { Text("Bluetooth") }
                )
                FilterChip(
                    selected = isWifi,
                    onClick = {
                        mode = ConnectionMode.WIFI_LAB
                        wifiReady = false
                        wifiState = null
                        wifiSecret = ""
                    },
                    label = { Text("Wi-Fi") }
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        if (connected) "●  Conectado por " + (if (isWifi) "Wi-Fi" else "Bluetooth")
                        else "○  " + (if (isWifi) "Wi-Fi sin conectar" else "Bluetooth sin conectar"),
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (isWifi) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (isDebug) "Modo laboratorio (debug). SoftAP local 192.168.4.1; sin TLS ni Internet."
                            else "Wi-Fi no disponible en versiones de distribución hasta completar seguridad y QA.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (isDebug) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = wifiSecret,
                                onValueChange = {
                                    wifiSecret = it.trim().take(16)
                                    wifiReady = false
                                    wifiState = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Clave local de control (monitor USB)") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                enabled = !wifiBusy
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                enabled = !wifiBusy && LocalLabClient.validKey(wifiSecret),
                                onClick = {
                                    wifiReady = false
                                    wifiBusy = true
                                    scope.launch {
                                        try {
                                            val response = wifiClient.status(wifiSecret)
                                            wifiState = RgbDeviceState.parse(response.optString("rgb"))
                                                ?: error("Estado RGB no reconocido.")
                                            wifiReady = true
                                            wifiMessage = "Conectado al ESP32 local. Sin fallback a datos móviles."
                                        } catch (e: Exception) {
                                            wifiState = null
                                            wifiMessage = e.message ?: "No se pudo abrir la sesión Wi-Fi."
                                        } finally {
                                            wifiBusy = false
                                        }
                                    }
                                }
                            ) { Text(if (wifiReady) "Actualizar estado" else "Conectar Wi-Fi") }
                            Text(wifiMessage, style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onOpenDevices) {
                            Text(if (connected) "Cambiar dispositivo" else "Buscar dispositivo Bluetooth")
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LedPage.entries.forEach { item ->
                    FilterChip(
                        selected = page == item,
                        onClick = { page = item },
                        label = { Text(when (item) {
                            LedPage.CONTROL -> "Control"
                            LedPage.COLORS -> "Colores"
                            LedPage.EFFECTS -> "Efectos"
                        }) }
                    )
                }
            }
        }
        // Key the content by transport so switching BLE/Wi-Fi never leaks a pending slider command.
        key(mode) {
            when (page) {
                LedPage.CONTROL -> ControlScreen(bleManager, link)
                LedPage.COLORS -> PresetsScreen(bleManager, preferencesRepository, link)
                LedPage.EFFECTS -> EffectsScreen(bleManager, link)
            }
        }
    }
}
