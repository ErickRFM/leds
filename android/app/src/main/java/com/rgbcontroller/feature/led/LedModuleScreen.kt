package com.rgbcontroller.feature.led

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
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
import com.rgbcontroller.core.ui.CompiInlineMessage
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
    var connectionEpoch by remember { mutableIntStateOf(0) }
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
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text("LED RGB", style = MaterialTheme.typography.headlineSmall)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !isWifi,
                    onClick = {
                        if (isWifi) {
                            connectionEpoch++
                            mode = ConnectionMode.BLUETOOTH
                            wifiReady = false
                            wifiBusy = false
                            wifiState = null
                            wifiSecret = ""
                        }
                    },
                    label = { Text("Bluetooth") }
                )
                FilterChip(
                    selected = isWifi,
                    onClick = {
                        if (!isWifi) {
                            connectionEpoch++
                            mode = ConnectionMode.WIFI_LAB
                            wifiReady = false
                            wifiBusy = false
                            wifiState = null
                            wifiSecret = ""
                        }
                    },
                    label = { Text("Wi-Fi") }
                )
            }
            if (!isWifi) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (connected) "●  ESP32 conectado" else "○  Bluetooth sin conectar",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (connected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onOpenDevices) {
                        Text(if (connected) "Cambiar" else "Buscar ESP32")
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text(
                            if (connected) "●  Wi-Fi de laboratorio conectado"
                            else "○  Wi-Fi de laboratorio sin conectar",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            if (isDebug) "Red ESP32-Control-Lab · 192.168.4.1 · sin Internet ni TLS."
                            else "Wi-Fi no habilitado en versiones de distribución.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (isDebug) {
                            OutlinedTextField(
                                value = wifiSecret,
                                onValueChange = {
                                    connectionEpoch++
                                    wifiSecret = it.trim().take(16)
                                    wifiReady = false
                                    wifiState = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Clave local (monitor serie USB)") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                enabled = !wifiBusy
                            )
                            Button(
                                enabled = !wifiBusy && LocalLabClient.validKey(wifiSecret),
                                onClick = {
                                    connectionEpoch++
                                    val attempt = connectionEpoch
                                    val key = wifiSecret
                                    wifiReady = false
                                    wifiBusy = true
                                    scope.launch {
                                        try {
                                            val response = wifiClient.status(key)
                                            val fresh = RgbDeviceState.parse(response.optString("rgb"))
                                                ?: error("Estado RGB no reconocido.")
                                            if (connectionEpoch == attempt && mode == ConnectionMode.WIFI_LAB) {
                                                wifiState = fresh
                                                wifiReady = true
                                                wifiMessage = "Estado sincronizado con el ESP32."
                                            }
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (error: Exception) {
                                            if (connectionEpoch == attempt && mode == ConnectionMode.WIFI_LAB) {
                                                wifiState = null
                                                wifiMessage = error.message ?: "No se pudo conectar."
                                            }
                                        } finally {
                                            if (connectionEpoch == attempt) wifiBusy = false
                                        }
                                    }
                                }
                            ) { Text(if (wifiBusy) "Conectando…" else if (wifiReady) "Sincronizar" else "Conectar Wi-Fi") }
                            CompiInlineMessage(
                                wifiMessage,
                                isError = wifiMessage.startsWith("No se pudo") ||
                                    wifiMessage.startsWith("Se perdió") ||
                                    wifiMessage.contains("inválid", ignoreCase = true)
                            )
                        }
                    }
                }
            }
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
