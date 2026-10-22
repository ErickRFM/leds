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
 * Reusable soil-moisture (ADC1) card. It only shows a percentage when the
 * firmware reported a calibrated, valid reading; an uncalibrated sensor falls
 * back to the raw ADC value and an absent one to an explicit "sin datos" state,
 * never a fabricated number. [live] distinguishes the current session from the
 * persisted last confirmed snapshot.
 */
@Composable
fun SoilMoistureWidget(
    moisturePercent: Float? = null,
    rawAdc: Int? = null,
    calibrated: Boolean = false,
    dryValue: Int = 4095,
    wetValue: Int = 0,
    stateLabel: String = if (calibrated) "Calibrado" else "Sin calibrar",
    hasData: Boolean = moisturePercent != null || rawAdc != null,
    lastUpdate: Long? = null,
    live: Boolean = true,
    status: String? = null,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Humedad del Suelo (ADC1)", style = MaterialTheme.typography.titleMedium)
            if (!hasData) {
                Text(
                    "Sin datos - Sensor no detectado, desconectado o con lectura inválida",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                if (calibrated && moisturePercent != null) {
                    Text(
                        "Humedad: ${formatPercent(moisturePercent)} %",
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    Text(
                        "Sin calibrar - No hay porcentaje de humedad; solo lectura cruda del ADC1",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                rawAdc?.let { Text("ADC crudo: $it / $dryValue-$wetValue", style = MaterialTheme.typography.bodySmall) }
            }
            Text("Estado: $stateLabel", style = MaterialTheme.typography.bodySmall)
            if (!live) {
                Text(
                    "Último estado confirmado, no en vivo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            if (status != null) {
                Text(
                    "Estado del sensor: $status",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (lastUpdate != null && lastUpdate > 0L) {
                Text(
                    "Lectura: ${formatTime(lastUpdate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatPercent(value: Float): String = String.format(Locale.US, "%.1f", value)

private fun formatTime(epochMs: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(epochMs))
