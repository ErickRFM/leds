package com.rgbcontroller.feature.modules

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.components.model.ComponentRuntimeStatus
import com.rgbcontroller.core.components.registry.ComponentCatalog
import com.rgbcontroller.core.components.registry.ModuleWorkspaceGrouping
import com.rgbcontroller.core.components.status.ModuleEvidence

@Composable
internal fun ModuleVisualIcon(id: String) {
    val (icon, accent) = when (id) {
        ModuleWorkspaceGrouping.ENVIRONMENT -> Icons.Default.Eco to Color(0xFFA980F4)
        "dht11", "dht22" -> Icons.Default.Thermostat to Color(0xFFEC7797)
        "soil_moisture" -> Icons.Default.WaterDrop to Color(0xFF46C6EF)
        "led_rgb" -> Icons.Default.Lightbulb to Color(0xFFFAB862)
        "ssd1306_oled" -> Icons.Default.DesktopWindows to Color(0xFFFFA45B)
        "analog_sensor", "digital_sensor" -> Icons.Default.DeviceHub to Color(0xFF78A8F7)
        else -> Icons.Default.Widgets to Color(0xFFA980F4)
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.16f),
        modifier = Modifier.size(52.dp).testTag("module_symbol_$id")
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = accent,
                modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
internal fun ModuleRuntimeCard(
    id: String,
    runtime: ComponentRuntimeStatus?,
    live: Boolean,
    sensorResponding: Boolean = false
) {
    val verified = runtime?.active == true && live
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ModuleVisualIcon(id)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(ComponentCatalog.get(id)?.name ?: id,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text(if (runtime == null) "Configuración local · por verificar"
                         else ModuleEvidence.moduleStatus(id, runtime.active, live, sensorResponding),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (verified && (id !in setOf("led_rgb", "dht11", "dht22") ||
                    sensorResponding))
                    Icon(Icons.Default.CheckCircle,
                    contentDescription = if (sensorResponding) "Lectura confirmada"
                                         else "Controlador confirmado",
                    tint = Color(0xFF54D8A3))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                when {
                    runtime == null -> "El ESP32 aún no ha confirmado este componente."
                    !runtime.active && runtime.state == "catalog" ->
                        "Driver disponible, pero sensor no activado ni detectado."
                    runtime.active && id in setOf("dht11", "dht22") && sensorResponding ->
                        "Temperatura y humedad válidas recibidas del ESP32 por Wi-Fi."
                    runtime.active && id in setOf("dht11", "dht22") ->
                        "Driver ${id.uppercase()} configurado; aún no se confirma una lectura física."
                    runtime.active && id == "led_rgb" ->
                        "Salidas PWM preparadas. El ESP32 no dispone de sensor de corriente ni retorno para detectar si hay un LED conectado."
                    runtime.active -> "Estado del firmware: ${runtime.state}"
                    else -> "El firmware informa: ${runtime.state}"
                },
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                if (runtime?.active == true) "GPIO en firmware: ${runtime.pins.joinToString()}"
                else "GPIO sin asignación activa confirmada",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun StationMetric(
    label: String,
    value: String,
    image: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

