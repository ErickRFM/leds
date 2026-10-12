package com.rgbcontroller.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.ble.BleConnectionState
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.core.ui.CompiModuleCard
import com.rgbcontroller.core.ui.CompiInlineMessage
import com.rgbcontroller.core.ui.SectionHeader

@Composable
fun DashboardScreen(
    bleManager: BleManager,
    onLed: () -> Unit,
    onCode: () -> Unit,
    onDevices: () -> Unit,
    onHardware: () -> Unit
) {
    val bleConnection by bleManager.connectionState.collectAsState()
    val status = when (val state = bleConnection) {
        BleConnectionState.Ready -> "Bluetooth listo"
        BleConnectionState.Scanning -> "Buscando dispositivos"
        BleConnectionState.Connecting -> "Conectando ESP32"
        BleConnectionState.Discovering -> "Sincronizando ESP32"
        BleConnectionState.Disconnected -> "Sin dispositivo conectado"
        is BleConnectionState.Error -> "Bluetooth necesita atención"
    }
    val details = when (val state = bleConnection) {
        BleConnectionState.Ready -> "Tu ESP32 está listo para recibir controles RGB."
        BleConnectionState.Scanning -> "Buscando controladores compatibles cercanos."
        BleConnectionState.Connecting, BleConnectionState.Discovering -> "Espera a que termine la conexión."
        BleConnectionState.Disconnected -> "Puedes trabajar en Código o abrir LED para conectarte."
        is BleConnectionState.Error -> state.message
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader("Compi", "Controla, crea y experimenta con tu ESP32")
        Card(
            onClick = onLed,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Estado Bluetooth", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(status, style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(details, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Abrir controles y conexión  →", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Text("Herramientas", style = MaterialTheme.typography.titleMedium)
        CompiModuleCard("LED RGB", "Color, brillo, favoritos y efectos",
            Icons.Default.ColorLens, onClick = onLed, modifier = Modifier.testTag("open_led"))
        CompiModuleCard("Compi Studio", "Editar código, proyectos y scripts",
            Icons.Default.Code, onClick = onCode, modifier = Modifier.testTag("open_code"))
        Text("Dispositivo y laboratorio", style = MaterialTheme.typography.titleMedium)
        CompiModuleCard("Mis dispositivos", "Conectar, desconectar e identificar por Bluetooth",
            Icons.Default.Bluetooth, onClick = onDevices, modifier = Modifier.testTag("open_devices"))
        CompiModuleCard("Hardware Lab", "Wi-Fi local, GPIO, pines activos, sensores y diagnóstico",
            Icons.Default.SettingsInputComponent, onClick = onHardware,
            modifier = Modifier.testTag("open_hardware"))
        CompiInlineMessage(
            "La conexión Wi-Fi está disponible solo para pruebas en APK debug. " +
                "La edición de código no flashea automáticamente la placa."
        )
        Spacer(Modifier.height(8.dp))
    }
}

