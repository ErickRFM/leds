package com.rgbcontroller.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.ble.BleManager

@Composable
fun SettingsScreen(bleManager: BleManager) {
    val connectionState by bleManager.connectionState.collectAsState()
    val deviceState by bleManager.deviceState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Ajustes & Diagnósticos", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Información del ESP32", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Estado BLE: ${connectionState.javaClass.simpleName}")
                Text("Última Trama de Estado: $deviceState")
                Text("Firmware: ESP32 Arduino 3.x / RGB v1.0")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { bleManager.writeCommand("GET") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Solicitar Estado Actual (GET)")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Ayuda de Conexión", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("1. Asegúrese de que el ESP32 esté encendido y con LED RGB conectado en GPIO 27 (R), 25 (G), 26 (B).")
                Text("2. Conceda permisos de Bluetooth y Ubicación en Android.")
                Text("3. Busque el dispositivo 'RGB-ESP32' en la pantalla de dispositivos.")
            }
        }
    }
}
