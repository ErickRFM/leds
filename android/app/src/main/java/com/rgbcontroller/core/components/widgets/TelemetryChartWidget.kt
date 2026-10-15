package com.rgbcontroller.core.components.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                "${rangeLabel(geometry)} · ${geometry.validCount} válidas · " +
                    "${geometry.invalidCount} inválidas · ${geometry.staleCount} antiguas · " +
                    (if (live) "en vivo" else "histórico"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val note = when {
                geometry.disconnected ->
                    "Desconectado · sin telemetría en vivo (datos históricos listados abajo)."
                !geometry.hasData -> "Sin datos válidos en la ventana temporal."
                geometry.hasGap -> "Hay huecos: una lectura inválida u obsoleta interrumpe la línea."
                else -> null
            }
            note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = if (geometry.disconnected) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Canvas(
                Modifier.fillMaxWidth().height(120.dp)
                    .testTag("chart_${geometry.componentId}_${geometry.measurementType}")
            ) {
                val w = size.width
                val h = size.height
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
