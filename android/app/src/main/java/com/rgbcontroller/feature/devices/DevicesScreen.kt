package com.rgbcontroller.feature.devices

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.ble.BleConnectionState
import com.rgbcontroller.core.ble.BleManager

@SuppressLint("MissingPermission")
@Composable
fun DevicesScreen(bleManager: BleManager) {
    val connectionState by bleManager.connectionState.collectAsState()
    val devices by bleManager.discoveredDevices.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Controlador ESP32 BLE", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))

        // Connection Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when (connectionState) {
                    is BleConnectionState.Ready -> MaterialTheme.colorScheme.primaryContainer
                    is BleConnectionState.Error -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Estado: ${connectionState.javaClass.simpleName}",
                    style = MaterialTheme.typography.titleMedium
                )
                if (connectionState is BleConnectionState.Error) {
                    Text(
                        text = (connectionState as BleConnectionState.Error).message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { bleManager.startScan() },
                modifier = Modifier.weight(1f),
                enabled = connectionState !is BleConnectionState.Scanning
            ) {
                Text(if (connectionState is BleConnectionState.Scanning) "Buscando..." else "Buscar ESP32")
            }

            OutlinedButton(
                onClick = { bleManager.disconnect() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Desconectar")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Dispositivos encontrados:", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(devices) { device ->
                val name = device.name ?: "Dispositivo Desconocido"
                val address = device.address
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { bleManager.connect(device) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(name, style = MaterialTheme.typography.bodyLarge)
                            Text(address, style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = { bleManager.connect(device) }) {
                            Text("Conectar")
                        }
                    }
                }
            }
        }
    }
}
