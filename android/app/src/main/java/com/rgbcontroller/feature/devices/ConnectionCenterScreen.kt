package com.rgbcontroller.feature.devices

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.ui.Alignment
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
import kotlinx.coroutines.delay

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
    var wifiNetworks by remember { mutableStateOf<List<WifiNetworkCandidate>>(emptyList()) }
    var wifiScanBusy by remember { mutableStateOf(false) }
    var wifiScanError by remember { mutableStateOf<String?>(null) }
    val wifiManager = remember(context) {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    }
    val scanWifi = {
        scope.launch {
            wifiScanBusy = true
            wifiScanError = null
            try {
                check(wifiManager != null) { "Este teléfono no dispone de un servicio Wi-Fi." }
                // Android may throttle startScan(). Always label these results
                // as the phone's last available scan, not live ESP32 discovery.
                @Suppress("DEPRECATION")
                val started = wifiManager.startScan()
                if (started) delay(2200L)
                @Suppress("DEPRECATION")
                val found = wifiManager.scanResults.orEmpty().map {
                    WifiNetworkCandidate(it.SSID.orEmpty(), it.level)
                }
                wifiNetworks = WifiNetworkDiscovery.present(found)
                if (!started && wifiNetworks.isNotEmpty()) {
                    wifiScanError = "Android limitó el escaneo: las redes mostradas son resultados guardados, no una detección nueva del ESP32."
                }
                if (wifiNetworks.isEmpty()) {
                    wifiScanError = "No se encontraron redes. Activa Wi-Fi y ubicación del teléfono; " +
                        "si el ESP32 no aparece, revisa su monitor serie."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: SecurityException) {
                wifiScanError = "Android impidió el escaneo. Revisa el permiso de ubicación y Wi-Fi."
            } catch (failure: Exception) {
                wifiScanError = failure.message ?: "No se pudieron consultar las redes Wi-Fi."
            } finally {
                wifiScanBusy = false
            }
        }
    }
    val permissionRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) scanWifi()
        else wifiScanError = "Se necesita permiso de ubicación únicamente para buscar redes Wi-Fi."
    }

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(12.dp))
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
                        Text("Red: ESP32-Control-Lab · La clave aparece por serial (115200).",
                            style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                        }) { Text("Abrir ajustes Wi-Fi") }
                    }
                }
                Card(Modifier.fillMaxWidth().testTag("wifi_network_results")) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Redes detectadas por Android",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f))
                            TextButton(
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(context,
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                        ) == PackageManager.PERMISSION_GRANTED
                                    ) scanWifi()
                                    else permissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                },
                                enabled = !wifiScanBusy,
                                modifier = Modifier.testTag("wifi_scan_real")
                            ) { Text(if (wifiScanBusy) "Buscando…" else "Buscar") }
                        }
                        if (wifiScanBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        wifiNetworks.forEach { network ->
                            Surface(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = if (network.ssid == WifiNetworkDiscovery.COMPI_SOFT_AP)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Wifi, contentDescription = null,
                                        modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(network.ssid, style = MaterialTheme.typography.bodyMedium)
                                        Text("${network.rssi} dBm · abrir ajustes para conectar",
                                            style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                        wifiScanError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.testTag("wifi_scan_error"))
                        }
                        Text(
                            "Redes del último escaneo del teléfono, no dispositivos verificados. " +
                                "Conéctate a ESP32-Control-Lab y confirma el firmware abajo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                    Text("Wi-Fi de laboratorio disponible solo en APK debug.",
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
                Text("USB", style = MaterialTheme.typography.titleMedium)
                Text("Para flashear y diagnosticar, utiliza Arduino IDE o un monitor serie en tu PC. " +
                    "USB-OTG aún no controla Compi desde Android.",
                    style = MaterialTheme.typography.bodySmall)
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
