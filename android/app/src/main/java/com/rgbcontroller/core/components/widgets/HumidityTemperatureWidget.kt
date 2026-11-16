package com.rgbcontroller.core.components.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.ui.CompiTokens
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
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CompiTokens.Shapes.Large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = CompiTokens.Elevations.flat)
    ) {
        Column(Modifier.padding(CompiTokens.Spacing.m),
            verticalArrangement = Arrangement.spacedBy(CompiTokens.Spacing.s)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Ambiente · $model", style = MaterialTheme.typography.titleMedium)
                Text(if (live && hasData) "● En vivo" else "Sin lectura en vivo",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (live && hasData) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CompiTokens.Spacing.s)) {
                MetricReading(
                    "Temperatura",
                    if (hasData && temperature?.isFinite() == true) format(temperature) else "—",
                    "°C",
                    Modifier.weight(1f)
                )
                MetricReading(
                    "Humedad",
                    if (hasData && humidity?.isFinite() == true) format(humidity) else "—",
                    "%",
                    Modifier.weight(1f)
                )
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
