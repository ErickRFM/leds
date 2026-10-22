package com.rgbcontroller.core.components.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reusable OLED SSD1306 (I2C) card. It keeps the three firmware-proven stages
 * visibly separate - detected, configured and operative - and only ever shows
 * content the firmware actually published. With no published text it renders an
 * explicit "sin contenido" preview instead of inventing a frame. [live]
 * distinguishes the current session from the persisted last confirmed snapshot.
 */
@Composable
fun OledDisplayWidget(
    configured: Boolean = false,
    text: String = "",
    hasData: Boolean = false,
    detected: Boolean = configured,
    operative: Boolean = false,
    sda: Int? = null,
    scl: Int? = null,
    address: Int? = null,
    stateLabel: String? = null,
    lastSync: Long? = null,
    live: Boolean = true,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("OLED SSD1306 (I²C)", style = MaterialTheme.typography.titleMedium)
            if (!detected) {
                Text(
                    "No detectado por el firmware - modulo ausente o deshabilitado (COMPI_ENABLE_OLED=0)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "Detectado por el firmware",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Modulo: ${if (configured) "configurado" else "no configurado"}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Pantalla: ${if (operative) "operativa (driver I2C inicializado)" else "no operativa"}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    if (sda != null && scl != null) "I²C: SDA GPIO$sda / SCL GPIO$scl"
                    else "I²C: pines no confirmados por el firmware",
                    style = MaterialTheme.typography.bodySmall
                )
                if (address != null) {
                    Text(
                        "Direccion I²C: 0x${address.toString(16).uppercase()} (declarada por el catalogo)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (stateLabel != null) {
                Text(
                    "Estado del firmware: $stateLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("Vista previa de contenido:", style = MaterialTheme.typography.bodySmall)
            if (hasData && text.isNotEmpty()) {
                TextField(value = text, onValueChange = {}, readOnly = true, modifier = Modifier.fillMaxWidth())
            } else {
                Text(
                    "Sin contenido publicado por el firmware (no se simula texto)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!live) {
                Text(
                    "Ultimo estado confirmado, no en vivo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            if (lastSync != null && lastSync > 0L) {
                Text(
                    "Ultima consulta: ${formatTime(lastSync)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatTime(epochMs: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(epochMs))
