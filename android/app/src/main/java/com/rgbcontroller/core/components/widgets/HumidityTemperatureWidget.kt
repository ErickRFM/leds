package com.rgbcontroller.core.components.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
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
    model: String = "DHT22",
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Ambiente · $model", style = MaterialTheme.typography.titleMedium)
                Text(if (live && hasData) "● En vivo" else "Sin lectura en vivo",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (live && hasData) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Temperatura", style = MaterialTheme.typography.labelMedium)
                    Text(if (hasData && temperature?.isFinite() == true) "${format(temperature)} °C" else "— °C",
                        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Text("Humedad", style = MaterialTheme.typography.labelMedium)
                    Text(if (hasData && humidity?.isFinite() == true) "${format(humidity)} %" else "— %",
                        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
            }
            if (!hasData || humidity?.isFinite() != true || temperature?.isFinite() != true) {
                Text("Esperando mediciones válidas del sensor",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!calibrated) Text("Lectura pendiente de validación",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall)
            if (!live && hasData) Text("Valores guardados · no son lecturas actuales",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (status != null || lastUpdate != null) {
                Text(listOfNotNull(status, lastUpdate?.takeIf { it > 0L }?.let { formatTime(it) })
                    .joinToString(" · "), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun format(value: Float): String = String.format(Locale.US, "%.1f", value)

private fun formatTime(epochMs: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(epochMs))
