package com.rgbcontroller.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.ble.BleConnectionState
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.core.data.PreferencesRepository
import com.rgbcontroller.core.ui.SectionHeader
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(bleManager: BleManager, preferencesRepository: PreferencesRepository) {
    val connection by bleManager.connectionState.collectAsState()
    val frame by bleManager.deviceState.collectAsState()
    val theme by preferencesRepository.themeMode.collectAsState(initial = "SYSTEM")
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 18.dp, vertical = 20.dp)) {
        SectionHeader("Ajustes", "Personaliza la app y consulta tu conexión")
        Spacer(Modifier.height(20.dp))
        Text("Apariencia", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf("SYSTEM" to "Sistema", "DARK" to "Oscuro", "LIGHT" to "Claro").forEachIndexed { index, item ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index, 3),
                    selected = theme == item.first,
                    onClick = { scope.launch { preferencesRepository.saveThemeMode(item.first) } },
                    label = { Text(item.second) }
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        Text("Estado del controlador", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp)) {
                Text("Conexión: " + when (connection) {
                    BleConnectionState.Ready -> "Lista"
                    BleConnectionState.Scanning -> "Buscando"
                    BleConnectionState.Connecting -> "Conectando"
                    BleConnectionState.Discovering -> "Sincronizando"
                    BleConnectionState.Disconnected -> "Desconectado"
                    is BleConnectionState.Error -> "Error"
                })
                Spacer(Modifier.height(6.dp))
                Text("Protocolo BLE: STATE / SET / FX", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text("Último estado: $frame", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    enabled = connection == BleConnectionState.Ready,
                    onClick = { bleManager.writeCommand("GET") }
                ) { Text("Actualizar desde ESP32") }
            }
        }
        Spacer(Modifier.height(22.dp))
        Text("Si no aparece tu dispositivo", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "1. Comprueba la alimentación y el anuncio RGB-ESP32.\n" +
            "2. Activa Bluetooth y concede los permisos de la app.\n" +
            "3. Busca de nuevo desde Dispositivos.\n" +
            "4. Si continúa sin aparecer, revisa el firmware y el UUID del servicio.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
