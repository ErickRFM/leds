package com.rgbcontroller.core.components.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun SoilMoistureWidget(
    moisturePercent: Float? = null,
    rawAdc: Int? = null,
    calibrated: Boolean = false,
    dryValue: Int = 4095,
    wetValue: Int = 0,
    stateLabel: String = if (calibrated) "Calibrado" else "Sin calibrar",
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Humedad del Suelo", style = MaterialTheme.typography.titleMedium)
            if (!calibrated || moisturePercent == null) {
                Text(
                    "Sin calibrar - Solo lectura cruda del ADC1",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "Humedad: ${String.format(Locale.US, "%.1f", moisturePercent)} %",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            rawAdc?.let { Text("ADC crudo: $it", style = MaterialTheme.typography.bodySmall) }
            Text("Estado: $stateLabel", style = MaterialTheme.typography.bodySmall)
        }
    }
}
