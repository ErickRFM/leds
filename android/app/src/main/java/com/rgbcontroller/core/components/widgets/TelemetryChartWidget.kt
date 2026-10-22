package com.rgbcontroller.core.components.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import java.util.Locale
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.components.telemetry.SampleQuality
import com.rgbcontroller.core.components.telemetry.TelemetryChartGeometry

/**
 * Renders a [TelemetryChartGeometry]. A gap is never bridged with a straight
 * line: an invalid/stale sample is drawn as a marker on the baseline, so a chart
 * cannot pretend an old or broken reading was live.
 */
@Composable
fun TelemetryChartCard(
    title: String,
    geometry: TelemetryChartGeometry,
    live: Boolean,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val invalidColor = MaterialTheme.colorScheme.error
    val staleColor = MaterialTheme.colorScheme.tertiary

    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    !geometry.hasData -> "Sin muestras en este periodo"
                    live -> "● En vivo  ·  ${geometry.validCount} muestras"
                    else -> "Historial  ·  ${geometry.validCount} muestras"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (live && geometry.hasData) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (geometry.hasData) {
                geometry.latestValid?.let { latest ->
                    Text("Actual: ${formatChartValue(latest.value)} ${geometry.unit}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "↓ ${formatChartValue(geometry.minValue)} ${geometry.unit}    " +
                    "↑ ${formatChartValue(geometry.maxValue)} ${geometry.unit}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (geometry.hasData && geometry.requestedWindowMs > 0) {
                val observed = (geometry.visibleDurationMs / 60_000f).coerceAtLeast(0f)
                val selected = geometry.requestedWindowMs / 60_000
                Text("Mostrando ${String.format(Locale.getDefault(), \"%.1f\", observed)} min disponibles · periodo $selected min",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val note = when {
                geometry.disconnected ->
                    "Sin conexión en vivo"
                !geometry.hasData -> "Sin datos válidos en la ventana temporal."
                geometry.hasGap -> "Lecturas interrumpidas"
                else -> null
            }
            note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = if (geometry.disconnected) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!geometry.hasData && geometry.classified.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(68.dp)
                        .testTag("chart_${geometry.componentId}_${geometry.measurementType}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Esperando mediciones reales",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else Canvas(
                Modifier.fillMaxWidth().height(156.dp)
                    .testTag("chart_${geometry.componentId}_${geometry.measurementType}")
            ) {
                val w = size.width
                val h = size.height
                val grid = lineColor.copy(alpha = 0.12f)
                for (fraction in listOf(0.25f, 0.5f, 0.75f)) {
                    drawLine(grid, Offset(0f, h * fraction), Offset(w, h * fraction), strokeWidth = 1f)
                }
                for (segment in geometry.segments) {
                    if (segment.size == 1) {
                        val p = segment[0]
                        drawCircle(lineColor, radius = 3f,
                            center = Offset(p.fractionX * w, (1f - p.fractionY) * h))
                        continue
                    }
                    val path = Path()
                    segment.forEachIndexed { i, p ->
                        val x = p.fractionX * w
                        val y = (1f - p.fractionY) * h
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, lineColor, style = Stroke(width = 2.5f))
                    for (p in segment) {
                        drawCircle(lineColor, radius = 2.5f,
                            center = Offset(p.fractionX * w, (1f - p.fractionY) * h))
                    }
                }
                for (cs in geometry.classified) {
                    if (cs.quality == SampleQuality.VALID) continue
                    val color = if (cs.quality == SampleQuality.STALE) staleColor else invalidColor
                    drawCircle(color, radius = 3.5f, center = Offset(cs.fractionX * w, h - 4f))
                }
            }
            if (geometry.hasData) {
                Text("Inicio del registro visible                                     Ahora",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (geometry.invalidCount > 0 || geometry.staleCount > 0) {
                Text(
                    "${geometry.invalidCount} inválidas · ${geometry.staleCount} antiguas",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun rangeLabel(geometry: TelemetryChartGeometry): String {
    if (!geometry.hasData) return "sin rango"
    val min = "%.2f".format(geometry.minValue)
    val max = "%.2f".format(geometry.maxValue)
    val unit = geometry.unit.ifBlank { "" }
    return "$min..$max $unit".trim()
}

private fun formatChartValue(value: Float): String =
    String.format(Locale.getDefault(), "%.1f", value)
