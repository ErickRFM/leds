package com.rgbcontroller.core.components.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OledDisplayWidget(
    configured: Boolean = false,
    text: String = "",
    hasData: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("OLED SSD1306", style = MaterialTheme.typography.titleMedium)
            if (!configured) {
                Text("No configurado - Requiere dirección I2C", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Vista previa:", style = MaterialTheme.typography.bodySmall)
                TextField(value = text, onValueChange = {}, readOnly = true, modifier = Modifier.fillMaxWidth())
                if (!hasData) {
                    Text("Sin confirmación de visualización en dispositivo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
