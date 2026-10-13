package com.rgbcontroller.core.ui.modules

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.sensors.AdcSample

/** Reusable metric renderer; null is explicitly unavailable, never silently zero. */
@Composable
fun CompiMetricValue(label: String, value: String?, unit: String, source: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(if (value == null) "Sin lectura" else "$value $unit",
            style = MaterialTheme.typography.titleLarge)
        Text(source, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Small foreground-only chart that consumes bounded, source-verified ADC readings. */
@Composable
fun CompiAdcTrend(samples: List<AdcSample>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxWidth().height(90.dp).semantics {
        contentDescription = "Historial de " + samples.size + " lecturas ADC"
    }) {
        if (samples.size >= 2) {
            for (i in 1 until samples.size) {
                val x0 = (i - 1).toFloat() / (samples.size - 1) * size.width
                val x1 = i.toFloat() / (samples.size - 1) * size.width
                drawLine(lineColor,
                    Offset(x0, size.height * (1f - samples[i - 1].raw / 4095f)),
                    Offset(x1, size.height * (1f - samples[i].raw / 4095f)),
                    strokeWidth = 3.dp.toPx())
            }
        }
    }
}
