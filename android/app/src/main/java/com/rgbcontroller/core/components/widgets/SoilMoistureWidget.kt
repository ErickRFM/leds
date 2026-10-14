package com.rgbcontroller.core.components.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SoilMoistureWidget(
    moisturePercent: Float? = null,
    rawAdc: Int? = null,
    calibrated: Boolean = false,
    dryValue: Int = 4095,
    wetValue: Int = 0,
    stateLabel: String = "Sin calibrar",
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Humedad del Suelo", style = MaterialTheme.typography.titleMedium)
            if (!calibrated || moisturePercent == null) {
                Text("Requiere calibración (seco/húmedo)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                rawAdc?.let { Text("ADC crudo: ", style = MaterialTheme.typography.bodySmall) }
                Text("Estado: ", style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Humedad estimada: %", style = MaterialTheme.typography.bodyLarge)
                rawAdc?.let { Text("ADC crudo: ", style = MaterialTheme.typography.bodySmall) }
                Text("Estado: ", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
