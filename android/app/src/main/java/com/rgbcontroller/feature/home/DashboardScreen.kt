package com.rgbcontroller.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.ble.BleConnectionState
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.core.ui.CompiModuleCard
import com.rgbcontroller.core.ui.CompiInlineMessage

/**
 * Compi V9 home: the visual reference is the layout contract, but device states
 * always originate in BleManager. Never display fabricated Wi-Fi telemetry.
 */
@Composable
fun DashboardScreen(
    bleManager: BleManager,
    onLed: () -> Unit,
    onModules: () -> Unit,
    onCode: () -> Unit,
    onDevices: () -> Unit,
    onHardware: () -> Unit,
    onSettings: () -> Unit = {}
) {
    val connection by bleManager.connectionState.collectAsState()
    val ready = connection == BleConnectionState.Ready
    val status = when (connection) {
        BleConnectionState.Ready -> "ESP32 listo"
        BleConnectionState.Scanning -> "Buscando ESP32"
        BleConnectionState.Connecting -> "Conectando ESP32"
        BleConnectionState.Discovering -> "Sincronizando ESP32"
        BleConnectionState.Disconnected -> "ESP32 sin conexión"
        is BleConnectionState.Error -> "Conexión necesita atención"
    }
    val subtitle = when (val current = connection) {
        BleConnectionState.Ready -> "Bluetooth conectado y confirmado"
        BleConnectionState.Scanning -> "Buscando dispositivos cercanos"
        BleConnectionState.Connecting -> "Estableciendo conexión Bluetooth"
        BleConnectionState.Discovering -> "Verificando servicios y estado"
        BleConnectionState.Disconnected -> "Conecta tu ESP32 para usar los controles"
        is BleConnectionState.Error -> current.message
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Compi", style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            IconButton(onClick = onSettings, modifier = Modifier.testTag("home_settings")) {
                Icon(Icons.Default.Settings, contentDescription = "Ajustes")
            }
        }
        Card(
            onClick = onDevices,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().testTag("home_device_status"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("CONEXIÓN Y DISPOSITIVO", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null,
                        tint = if (ready) Color(0xFF27D987) else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text(status, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bluetooth, contentDescription = null, tint = Color(0xFF57A4FF))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Bluetooth", fontWeight = FontWeight.SemiBold)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                Text("Wi-Fi de laboratorio: consulta su estado desde Hardware",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAction("Módulos", Icons.Default.Memory, onModules, Modifier.weight(1f))
            QuickAction("Studio", Icons.Default.Code, onCode, Modifier.weight(1f))
            QuickAction("Hardware", Icons.Default.Memory, onHardware, Modifier.weight(1f))
            QuickAction("Equipos", Icons.Default.Devices, onDevices,
                Modifier.weight(1f).testTag("open_devices"))
        }

        Text("Herramientas principales", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold)
        CompiModuleCard("Biblioteca de módulos", "Intercambia LED, sensores y mediciones",
            Icons.Default.Memory, onClick = onModules, modifier = Modifier.testTag("open_modules"))
        CompiModuleCard("LED RGB", "Color, brillo, favoritos y efectos",
            Icons.Default.ColorLens, onClick = onLed, modifier = Modifier.testTag("open_led"))
        CompiModuleCard("Compi Studio", "Editar código, importar y ejecutar scripts compatibles",
            Icons.Default.Code, onClick = onCode, modifier = Modifier.testTag("open_code"))
        CompiModuleCard("Hardware Lab", "GPIO, sensores, Wi-Fi y diagnóstico",
            Icons.Default.Memory, onClick = onHardware, modifier = Modifier.testTag("open_hardware"))

        Text("Actividad reciente", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold)
        Card(shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(if (ready) "Dispositivo Bluetooth sincronizado" else "Sin actividad verificada",
                    fontWeight = FontWeight.Medium)
                Text("Los eventos de hardware se mostrarán cuando sean confirmados; no se simulan registros.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        CompiInlineMessage("Wi-Fi local y las operaciones de hardware requieren un ESP32 compatible. " +
            "El editor no instala firmware automáticamente.")
    }
}

@Composable
private fun QuickAction(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 76.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold, maxLines = 2)
        }
    }
}
