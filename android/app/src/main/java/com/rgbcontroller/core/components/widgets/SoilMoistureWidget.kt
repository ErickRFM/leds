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
                Text("Humedad del suelo · ADC1", style = MaterialTheme.typography.titleMedium)
                Text(if (live && hasData) "● En vivo" else "Sin lectura en vivo",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (live && hasData) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!hasData) {
                Text(
                    "Sin datos - Sensor no detectado, desconectado o con lectura inválida",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CompiTokens.Spacing.s)) {
                    MetricReading(
                        "Humedad",
                        if (calibrated && moisturePercent?.isFinite() == true)
                            formatPercent(moisturePercent) else "—",
                        "%",
                        Modifier.weight(1f)
                    )
                    MetricReading("ADC crudo", rawAdc?.toString() ?: "—", "/ 4095", Modifier.weight(1f))
                }
                if (!calibrated) Text(
                    "Sin calibrar - No hay porcentaje de humedad; solo lectura cruda del ADC1",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("Referencias · seco $dryValue, húmedo $wetValue",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!live) {
                Text(
                    "Último estado confirmado, no en vivo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            // The uncalibrated sentence already states the state; repeating it
            // here would be two labels for one fact.
            val meta = listOfNotNull(
                if (calibrated) "Estado: $stateLabel" else null,
                status?.let { "Sensor: $it" },
                lastUpdate?.takeIf { it > 0L }?.let { formatTime(it) }
            )
            if (meta.isNotEmpty()) Text(meta.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatPercent(value: Float): String = String.format(Locale.US, "%.1f", value)

private fun formatTime(epochMs: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(epochMs))
