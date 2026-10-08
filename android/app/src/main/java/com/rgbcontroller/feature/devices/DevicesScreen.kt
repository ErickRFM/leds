package com.rgbcontroller.feature.devices

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.ble.BleConnectionState
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.core.data.PreferencesRepository
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
@Composable
fun DevicesScreen(bleManager: BleManager, preferencesRepository: PreferencesRepository) {
    val state by bleManager.connectionState.collectAsState()
    val devices by bleManager.discoveredDevices.collectAsState()
    val lastMac by preferencesRepository.lastDeviceMac.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Mis dispositivos", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                val description = when (val current = state) {
                    is BleConnectionState.Disconnected -> "Desconectado"
                    is BleConnectionState.Scanning -> "Buscando ESP32 cercano..."
                    is BleConnectionState.Connecting -> "Conectando con ESP32..."
                    is BleConnectionState.Discovering -> "Sincronizando servicios y estado..."
                    is BleConnectionState.Ready -> "ESP32 listo para recibir comandos"
                    is BleConnectionState.Error -> "Error: ${current.message}"
                }
                Text(description, style = MaterialTheme.typography.titleSmall)
                if (lastMac != null) {
                    Spacer(Modifier.height(6.dp))
                    Text("Última conexión: $lastMac", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { bleManager.startScan() },
                enabled = state is BleConnectionState.Disconnected ||
                    state is BleConnectionState.Error,
                modifier = Modifier.weight(1f)
            ) { Text("Buscar ESP32") }
            OutlinedButton(
                onClick = { bleManager.disconnect() },
                enabled = state !is BleConnectionState.Disconnected &&
                    state !is BleConnectionState.Scanning,
                modifier = Modifier.weight(1f)
            ) { Text("Desconectar") }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Dispositivos RGB compatibles encontrados",
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(Modifier.height(8.dp))
        if (devices.isEmpty()) {
            Text(
                if (state is BleConnectionState.Scanning) "Buscando por UUID del controlador..."
                else "No se ha encontrado un ESP32 RGB. Revisa su alimentación y firmware.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 18.dp)
        ) {
            items(devices, key = { it.address }) { device ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(device.name ?: "ESP32 sin nombre", style = MaterialTheme.typography.titleSmall)
                        Text(device.address, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            enabled = state !is BleConnectionState.Connecting &&
                                state !is BleConnectionState.Discovering,
                            onClick = {
                                bleManager.connect(device)
                                scope.launch { preferencesRepository.saveLastDeviceMac(device.address) }
                            }
                        ) { Text("Conectar") }
                    }
                }
            }
        }
    }
}
