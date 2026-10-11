package com.rgbcontroller.feature.devices

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.ble.BleConnectionState
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.core.data.PreferencesRepository
import com.rgbcontroller.core.ui.SectionHeader

@SuppressLint("MissingPermission")
@Composable
fun DevicesScreen(
    bleManager: BleManager,
    preferencesRepository: PreferencesRepository,
    onInspectSecurity: () -> Unit
) {
    val connection by bleManager.connectionState.collectAsState()
    val devices by bleManager.discoveredDevices.collectAsState()
    val lastMac by preferencesRepository.lastDeviceMac.collectAsState(initial = null)
    var searched by rememberSaveable { mutableStateOf(false) }
    var requestedAddress by remember { mutableStateOf<String?>(null) }

    // Remember only a device that actually reached READY, not a failed attempt.
    LaunchedEffect(connection, requestedAddress) {
        val address = requestedAddress
        if (connection == BleConnectionState.Ready && address != null) {
            preferencesRepository.saveLastDeviceMac(address)
            requestedAddress = null
        }
    }
    val busy = connection is BleConnectionState.Connecting || connection is BleConnectionState.Discovering
    val ready = connection == BleConnectionState.Ready
    val searching = connection == BleConnectionState.Scanning
    val status = when (val s = connection) {
        is BleConnectionState.Disconnected -> "Sin conexión"
        is BleConnectionState.Scanning -> "Buscando controladores"
        is BleConnectionState.Connecting -> "Estableciendo conexión"
        is BleConnectionState.Discovering -> "Sincronizando ESP32"
        is BleConnectionState.Ready -> "ESP32 conectado"
        is BleConnectionState.Error -> "Necesita atención"
    }
    val detail = when (val s = connection) {
        is BleConnectionState.Disconnected -> "Activa tu ESP32 y busca dispositivos cercanos."
        is BleConnectionState.Scanning -> "El escaneo dura hasta 10 segundos."
        is BleConnectionState.Connecting -> "Conectando por Bluetooth Low Energy…"
        is BleConnectionState.Discovering -> "Verificando servicios y estado real…"
        is BleConnectionState.Ready -> "Listo para controlar colores y efectos."
        is BleConnectionState.Error -> s.message
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 20.dp)) {
        SectionHeader("Mis dispositivos", "Conecta y controla tu iluminación RGB")
        Spacer(Modifier.height(20.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp)) {
                    Icon(if (ready) Icons.Default.CheckCircle else Icons.Default.Bluetooth,
                        contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(14.dp).size(26.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(status, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(3.dp))
                    Text(detail, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (lastMac != null && !ready) {
            Spacer(Modifier.height(8.dp))
            Text("Último dispositivo conocido: …${lastMac!!.takeLast(5)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Conexión de laboratorio (BLE V1)",
                    style = MaterialTheme.typography.titleSmall)
                Text("Estar conectado NO significa ser propietario del ESP32. " +
                    "La vinculación segura V4 todavía está pendiente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onInspectSecurity) {
                    Text("Consultar identidad y seguridad")
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    if (searching) bleManager.stopScan() else {
                        searched = true
                        bleManager.startScan()
                    }
                },
                enabled = !busy && !ready,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                shape = RoundedCornerShape(18.dp)
            ) { Text(if (searching) "Detener" else if (searched) "Buscar otra vez" else "Buscar ESP32") }
            OutlinedButton(
                onClick = { bleManager.disconnect() },
                enabled = busy || ready || connection is BleConnectionState.Error,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                shape = RoundedCornerShape(18.dp)
            ) { Text("Desconectar") }
        }
        Spacer(Modifier.height(26.dp))
        Text("Disponibles cerca", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        if (devices.isEmpty()) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.BluetoothSearching, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        when {
                            searching -> "Buscando tu ESP32…"
                            connection is BleConnectionState.Error -> "Comprueba Bluetooth, permisos y firmware"
                            searched -> "No encontramos dispositivos compatibles"
                            else -> "Todavía no has buscado dispositivos"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (searched && !searching) {
                        Spacer(Modifier.height(6.dp))
                        Text("Asegúrate de que RGB-ESP32 esté encendido y anunciando su servicio BLE.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            items(devices, key = { it.address }) { device ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(device.name ?: "ESP32 sin nombre", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(3.dp))
                            Text("Dispositivo RGB compatible", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            requestedAddress = device.address
                            bleManager.connect(device)
                        }, enabled = !busy && !ready) { Text("Conectar") }
                    }
                }
            }
        }
    }
}
