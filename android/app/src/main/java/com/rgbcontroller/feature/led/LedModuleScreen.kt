package com.rgbcontroller.feature.led

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.ble.BleConnectionState
import com.rgbcontroller.core.ble.BleSecurityState
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
import com.rgbcontroller.core.ui.CompiV9Segment
import com.rgbcontroller.core.ui.CompiInlineMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Unified LED screen: Control, Color, Favorites, and Effects presented together in a single scrollable view matching V9 reference UI. */
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
    val bleSecurity by bleManager.securityState.collectAsState()
    val authorizationMessage by bleManager.authorizationMessage.collectAsState()
    val bleState by bleManager.rgbState.collectAsState()
    val bleRevision by bleManager.stateRevision.collectAsState()
    val mode = labSession.transport
    val wifiSecret = labSession.key
    val wifiReady = labSession.wifiReady
    val wifiState = labSession.rgbState
    val wifiMessage = labSession.message
    var wifiBusy by remember { mutableStateOf(false) }

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

    val wifiCommands = remember(mode, wifiReady) { Channel<String>(capacity = 32) }
    LaunchedEffect(mode, wifiReady, wifiCommands) {
        if (mode != LabTransport.WIFI_LAB || !wifiReady) return@LaunchedEffect
        val key = wifiSecret
        val epoch = labSession.revision
        try {
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
    val controlReady = if (isWifi) wifiReady else
        connected && bleSecurity == BleSecurityState.Authorized
    val state = if (isWifi) wifiState else bleState
    val link = RgbControlLink(
        transport = if (isWifi) "WIFI_LAB" else "BLUETOOTH",
        ready = controlReady,
        state = state,
        stateRevision = if (isWifi) labSession.stateRevision else bleRevision,
        sendCommand = { command ->
            if (isWifi) {
                if (wifiReady && !wifiCommands.trySend(command).isSuccess) {
                    labSession.invalidate("Demasiados comandos pendientes; vuelve a sincronizar Wi-Fi.")
                }
            } else if (controlReady) {
                bleManager.writeCommand(command)
            }
        }
    )

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header and Transport Toggles matching Reference Image 2
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("LED RGB", style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = onOpenDevices) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Settings,
                    contentDescription = "Ajustes"
                )
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CompiV9Segment(
                text = "Bluetooth",
                selected = !isWifi,
                modifier = Modifier.weight(1f),
                onClick = {
                    if (isWifi) {
                        labSession.selectTransport(LabTransport.BLUETOOTH)
                        wifiBusy = false
                    }
                }
            )
            CompiV9Segment(
                text = "Wi-Fi",
                selected = isWifi,
                modifier = Modifier.weight(1f),
                onClick = {
                    if (!isWifi) {
                        labSession.selectTransport(LabTransport.WIFI_LAB)
                        wifiBusy = false
                    }
                }
            )
        }

        // ESP32 Connected Card with "Cambiar" action
        Card(
            Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = androidx.compose.foundation.shape.CircleShape
                    ) {
                        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            Text("⚡", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                modifier = Modifier.size(8.dp),
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = if (connected) Color(0xFF22C55E) else Color(0xFFEF4444)
                            ) {}
                            Text(
                                if (connected && !isWifi && !controlReady) "ESP32 conectado · sin autorización"
                                else if (connected) "ESP32 conectado" else "ESP32 sin conectar",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            )
                        }
                        Text(
                            if (isWifi) "Red Lab-Red · Wi-Fi" else "Compi-ESP32 · Bluetooth",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedButton(
                    onClick = onOpenDevices,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Cambiar >")
                }
            }
        }

        if (isWifi && isDebug) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    OutlinedTextField(
                        value = wifiSecret,
                        onValueChange = { labSession.updateKey(it) },
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
                        isError = wifiMessage.startsWith("No se pudo") || wifiMessage.startsWith("Se perdió")
                    )
                }
            }
        }

        // Key by transport mode to avoid stale commands
        key(mode) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (!isWifi && connected && !controlReady) {
                    Text(
                        authorizationMessage ?: "El enlace BLE está conectado, pero aún no se autorizó al propietario. Abre Conexiones para autorizar el control.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(onClick = onOpenDevices) {
                        Text("Autorizar Bluetooth")
                    }
                }
                // 1. Control de color (Wheel + Sliders)
                ControlScreen(bleManager, link, embedded = true)

                // 2. Favoritos
                PresetsScreen(bleManager, preferencesRepository, link, embedded = true)

                // 3. Efectos
                EffectsScreen(bleManager, link, embedded = true)
            }
        }
    }
}
