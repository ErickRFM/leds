package com.rgbcontroller.feature.hardwarelab

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rgbcontroller.core.components.model.ComponentRuntimeStatus
import com.rgbcontroller.core.components.model.ComponentState
import com.rgbcontroller.core.components.model.ComponentStatusCodec
import com.rgbcontroller.core.components.registry.ComponentCatalog

@Composable
internal fun AuthorityCard(connected: Boolean, authorized: Boolean, error: String?) {
    val title = when {
        authorized -> "Conectado y autorizado"
        connected -> "Conexión sin autorizar"
        else -> "ESP32 desconectado"
    }
    val color = if (authorized) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Card(Modifier.fillMaxWidth().testTag("component_authority"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = color)
            Text("Pines reales: firmware · cambios locales: borrador.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("component_error"))
            }
        }
    }
}

@Composable
internal fun FirmwareComponentCard(component: ComponentRuntimeStatus, live: Boolean) {
    val state = ComponentStatusCodec.stateFor(component)
    val catalogId = ComponentCatalog.catalogIdForFirmwareId(component.id)
    val humanName = ComponentCatalog.get(catalogId)?.name ?: component.id
    var showTechDetails by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth().testTag("component_${component.id}")) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(humanName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    if (showTechDetails) {
                        Text("ID Técnico: ${component.id}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(
                    when (state) {
                        ComponentState.ACTIVE -> if (live) "ACTIVO" else "ÚLTIMO"
                        ComponentState.ERROR -> "ERROR"
                        ComponentState.INCOMPATIBLE -> "NO SOPORTADO"
                        ComponentState.DISCONNECTED -> "DESCONECTADO"
                        else -> "DESCONOCIDO"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (live && component.active && component.state == "active")
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("Tipo: ${component.type.replaceFirstChar { it.uppercase() }} · Interfaz: ${component.interfaceType.uppercase()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Conexión GPIO: ${if (component.pins.isEmpty()) "—" else component.pins.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall)

            TextButton(
                onClick = { showTechDetails = !showTechDetails },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(if (showTechDetails) "Ocultar ID técnico" else "Detalles técnicos", fontSize = 11.sp)
            }
        }
    }
}

