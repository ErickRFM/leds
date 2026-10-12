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
import com.rgbcontroller.core.wifi.CompiLabSession
import com.rgbcontroller.core.wifi.LabTransport
import com.rgbcontroller.feature.control.ControlScreen
import com.rgbcontroller.feature.effects.EffectsScreen
import com.rgbcontroller.feature.presets.PresetsScreen
import com.rgbcontroller.core.ui.SectionHeader
import com.rgbcontroller.core.ui.CompiInlineMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

private enum class LedPage { CONTROL, COLORS, EFFECTS }

/** One LED feature with equivalent commands; Wi-Fi remains an explicitly gated debug lab. */
@Composable
fun LedModuleScreen(
    bleManager: BleManager,
    preferencesRepository: PreferencesRepository,
    labSession: CompiLabSession,
    onOpenDevices: () -> Unit
) {
    val context = LocalContext.current
    val isDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val wifiClient = remember(context) { LocalLabClient(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val bleConnection by bleManager.connectionState.collectAsState()
    val bleState by bleManager.rgbState.collectAsState()
    val mode = labSession.transport
    val wifiSecret = labSession.key
    val wifiReady = labSession.wifiReady
    val wifiState = labSession.rgbState
    val wifiMessage = labSession.message
    var page by remember { mutableStateOf(LedPage.CONTROL) }
    var wifiBusy by remember { mutableStateOf(false) }

    // Navigation or Activity recreation must not display an old 'connected' state.
    LaunchedEffect(Unit) {
        if (labSession.transport == LabTransport.WIFI_LAB && labSession.wifiReady) {
            val (epoch, key) = labSession.beginVerification()
            try {
                val response = labSession.serialized { wifiClient.status(key) }
                val parsed = RgbDeviceState.parse(response.optString("rgb"))
                    ?: error("Estado RGB no reconocido.")
                labSession.verified(epoch, key, parsed)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                labSession.failed(epoch, error.message ?: "No se pudo actualizar la sesión.")
            }
        }
    }

    // A separate queue for each connection epoch prevents replay after switching transport.
    val wifiCommands = remember(mode, wifiReady) { Channel<String>(capacity = 32) }
    LaunchedEffect(mode, wifiReady, wifiCommands) {
        if (mode != LabTransport.WIFI_LAB || !wifiReady) return@LaunchedEffect
        val key = wifiSecret
        val epoch = labSession.revision
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
                val response = labSession.serialized {
                    wifiClient.rgb(key, command)
                    wifiClient.status(key)
                }
                val parsed = RgbDeviceState.parse(response.optString("rgb"))
                    ?: error("Respuesta RGB inválida; vuelve a conectar.")
                if (command.startsWith("SET,")) {
                    val values = command.split(',').drop(1).map { it.toInt() }
                    check(com.rgbcontroller.core.model.RgbReadback.confirmsSet(
                        values[0], values[1], values[2], values[3], parsed
                    )) { "RGB no coincide con lo enviado; reconecta y comprueba la placa." }
                }
                if (!labSession.commandReadback(epoch, key, parsed)) {
                    return@LaunchedEffect
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            labSession.failed(epoch, error.message ?: "Se perdió la conexión Wi-Fi.")
        } finally {
            wifiCommands.cancel()
        }
    }

    val isWifi = mode == LabTransport.WIFI_LAB
    val connected = if (isWifi) wifiReady else bleConnection == BleConnectionState.Ready
    val state = if (isWifi) wifiState else bleState
    val link = RgbControlLink(
        transport = if (isWifi) "WIFI_LAB" else "BLUETOOTH",
        ready = connected,
        state = state,
        stateRevision = if (isWifi) labSession.stateRevision else 0L,
        sendCommand = { command ->
            if (isWifi) {
                if (wifiReady && !wifiCommands.trySend(command).isSuccess) {
                    labSession.invalidate("Demasiados comandos pendientes; vuelve a sincronizar Wi-Fi.")
                }
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
                            labSession.selectTransport(LabTransport.BLUETOOTH)
                            wifiBusy = false
                        }
                    },
                    label = { Text("Bluetooth") }
                )
                FilterChip(
                    selected = isWifi,
                    onClick = {
                        if (!isWifi) {
                            labSession.selectTransport(LabTransport.WIFI_LAB)
                            wifiBusy = false
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
                                    labSession.setKey(it)
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
                                    val (attempt, key) = labSession.beginVerification()
                                    wifiBusy = true
                                    scope.launch {
                                        try {
                                            val response = labSession.serialized { wifiClient.status(key) }
                                            val fresh = RgbDeviceState.parse(response.optString("rgb"))
                                                ?: error("Estado RGB no reconocido.")
                                            labSession.verified(attempt, key, fresh)
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (error: Exception) {
                                            labSession.failed(attempt, error.message ?: "No se pudo conectar.")
                                        } finally {
                                            wifiBusy = false
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
