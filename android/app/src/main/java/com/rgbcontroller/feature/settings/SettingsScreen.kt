package com.rgbcontroller.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.ble.BleConnectionState
import com.rgbcontroller.core.ble.BleManager

@Composable
fun SettingsScreen(bleManager: BleManager) {
    val connection by bleManager.connectionState.collectAsState()
    val state by bleManager.deviceState.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Ajustes y diagnóstico", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Estado del ESP32", style = MaterialTheme.typography.titleMedium)
                Text("Conexión: ${connection.javaClass.simpleName}")
                Text("Última trama: $state")
                Text("Protocolo: RGB BLE (STATE/SET/FX)")
                Text("Versión exacta de firmware: no informada por la placa")
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { bleManager.writeCommand("GET") },
            enabled = connection == BleConnectionState.Ready,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Actualizar estado (GET)") }
        Spacer(Modifier.height(16.dp))
        Text("Ayuda de conexión", style = MaterialTheme.typography.titleMedium)
        Text("1. Alimenta el ESP32. Conecta el RGB mediante tres resistencias a los GPIO 27, 25 y 26.")
        Text("2. Otorga permisos Bluetooth cuando Android lo solicite.")
        Text("3. Abre Dispositivos, busca RGB-ESP32 y pulsa Conectar.")
        Text("4. Si falla, verifica el firmware y su UUID GATT.")
    }
}
