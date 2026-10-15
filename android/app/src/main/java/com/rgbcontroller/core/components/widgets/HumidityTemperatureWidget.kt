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
 * Reusable DHT22 card. It only shows numbers the firmware reported as valid; with
 * no valid reading it renders an explicit "sin datos" state instead of inventing
 * a value. [live] distinguishes the current session from the persisted last
 * confirmed snapshot.
 */
@Composable
fun HumidityTemperatureWidget(
    humidity: Float? = null,
    temperature: Float? = null,
    hasData: Boolean = false,
    lastUpdate: Long? = null,
    calibrated: Boolean = true,
    live: Boolean = true,
    status: String? = null,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("DHT22 - Humedad y Temperatura", style = MaterialTheme.typography.titleMedium)
            if (!hasData || humidity == null || temperature == null) {
                Text(
                    "Sin datos - Sensor no detectado, desconectado o con lectura inválida",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Humedad: ${format(humidity)} %", style = MaterialTheme.typography.bodyLarge)
                    Text("Temp: ${format(temperature)} C", style = MaterialTheme.typography.bodyLarge)
                }
                if (!calibrated) {
                    Text(
                        "Advertencia: lecturas no validadas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
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

private fun format(value: Float): String = String.format(Locale.US, "%.1f", value)

private fun formatTime(epochMs: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(epochMs))
