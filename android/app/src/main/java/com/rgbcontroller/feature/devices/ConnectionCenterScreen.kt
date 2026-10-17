package com.rgbcontroller.feature.devices

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.core.data.PreferencesRepository
import com.rgbcontroller.core.model.RgbDeviceState
import com.rgbcontroller.core.wifi.CompiLabSession
import com.rgbcontroller.core.wifi.LabConnectionState
import com.rgbcontroller.core.wifi.LabTransport
import com.rgbcontroller.core.wifi.LocalLabClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class ConnectTab { BLUETOOTH, WIFI, USB }

/**
 * Connection hub without fabricated Wi-Fi scans or USB capabilities.
 * The existing BLE discovery and lab SoftAP handshake remain the only paths.
 */
@Composable
fun ConnectionCenterScreen(
    bleManager: BleManager,
    preferencesRepository: PreferencesRepository,
    session: CompiLabSession,
    onHardware: () -> Unit
) {
    val context = LocalContext.current
    val debug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val client = remember(context) { LocalLabClient(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable {
        mutableStateOf(if (session.transport == LabTransport.WIFI_LAB) ConnectTab.WIFI
        else ConnectTab.BLUETOOTH)
    }
    var busy by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Text("Conectar dispositivo", style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TransportChoice(
                label = "Bluetooth",
                icon = Icons.Default.Bluetooth,
                selected = tab == ConnectTab.BLUETOOTH,
                modifier = Modifier.weight(1f).testTag("connect_bluetooth"),
                onClick = {
                    tab = ConnectTab.BLUETOOTH
                    session.selectTransport(LabTransport.BLUETOOTH)
                }
            )
            TransportChoice(
                label = "Wi-Fi",
                icon = Icons.Default.Wifi,
                selected = tab == ConnectTab.WIFI,
                modifier = Modifier.weight(1f).testTag("connect_wifi"),
                onClick = {
                    tab = ConnectTab.WIFI
                    session.selectTransport(LabTransport.WIFI_LAB)
                }
            )
            TransportChoice(
                label = "USB",
                icon = Icons.Default.Usb,
                selected = tab == ConnectTab.USB,
                modifier = Modifier.weight(1f).testTag("connect_usb"),
                onClick = { tab = ConnectTab.USB }
            )
        }

        when (tab) {
            ConnectTab.BLUETOOTH -> Box(Modifier.weight(1f)) {
                DevicesScreen(bleManager, preferencesRepository, onInspectSecurity = onHardware)
            }
            ConnectTab.WIFI -> Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ESP32 · Wi-Fi local", style = MaterialTheme.typography.titleMedium)
                        Text("Conéctate primero a ESP32-Control-Lab en los ajustes del sistema. " +
                            "El control Wi-Fi es de laboratorio (solo APK debug) y usa la clave " +
                            "mostrada por el ESP32 en el monitor serie.",
                            style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                        }) { Text("Abrir ajustes Wi-Fi") }
                    }
                }
                if (debug) {
                    OutlinedTextField(
                        value = session.key,
                        onValueChange = { session.updateKey(it) },
                        label = { Text("Clave de control (16 dígitos hexadecimales)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("connect_wifi_key")
                    )
                    Button(
                        onClick = {
                            session.selectTransport(LabTransport.WIFI_LAB)
                            val (epoch, key) = session.beginVerification()
                            busy = true
                            connectionError = null
                            scope.launch {
                                try {
                                    val status = session.serialized { client.status(key) }
                                    val rgb = RgbDeviceState.parse(status.optString("rgb"))
                                        ?: error("Estado RGB no reconocido por el firmware.")
                                    if (!session.verified(epoch, key, rgb)) {
                                        connectionError = "La sesión cambió durante la conexión. Vuelve a intentar."
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (failure: Exception) {
                                    session.failed(epoch, failure)
                                    connectionError = failure.message ?: "Error de comunicación con el ESP32."
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy && LocalLabClient.validKey(session.key),
                        modifier = Modifier.fillMaxWidth().testTag("connect_wifi_verify")
                    ) {
                        Text(if (busy) "Verificando…" else "Conectar y verificar firmware")
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    Text("El control HTTP de laboratorio no está habilitado en compilaciones release.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(when (session.connectionState) {
                    LabConnectionState.AUTHORIZED -> "Wi-Fi verificado y autorizado · estado leído del ESP32."
                    LabConnectionState.AUTH_FAILED -> "Clave rechazada por el ESP32."
                    LabConnectionState.NETWORK_ERROR -> "Sin ruta o respuesta válida del ESP32."
                    else -> session.message
                }, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                connectionError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = onHardware, modifier = Modifier.fillMaxWidth()) {
                    Text("Abrir diagnóstico de Hardware")
                }
            }
            ConnectTab.USB -> Column(
                Modifier.fillMaxWidth().weight(1f).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("USB · diagnóstico", style = MaterialTheme.typography.titleLarge)
                Text("Compi no ofrece todavía control serial USB-OTG dentro de Android. " +
                    "Puedes utilizar el puerto USB del ESP32 con Arduino IDE para flashear " +
                    "el firmware o consultar el monitor serie; no se simula una conexión.",
                    style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onHardware) { Text("Ver herramientas de Hardware") }
            }
        }
    }
}

/** Compact single-line transport tabs that never split "Bluetooth" on narrow phones. */
@Composable
private fun TransportChoice(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 12.sp, color = color, maxLines = 1, softWrap = false)
        }
    }
}
