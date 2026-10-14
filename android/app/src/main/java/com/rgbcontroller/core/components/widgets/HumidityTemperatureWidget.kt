package com.rgbcontroller.core.components.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun HumidityTemperatureWidget(
    humidity: Float? = null,
    temperature: Float? = null,
    hasData: Boolean = false,
    lastUpdate: Long? = null,
    calibrated: Boolean = true,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("DHT22 - Humedad y Temperatura", style = MaterialTheme.typography.titleMedium)
            if (!hasData || humidity == null || temperature == null) {
                Text(
                    "Sin datos - Sensor no inicializado o desconectado",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "Humedad: ${format(humidity)} %",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "Temp: ${format(temperature)} C",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                if (!calibrated) {
                    Text(
                        "Advertencia: Lecturas no validadas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private fun format(value: Float): String = String.format(Locale.US, "%.1f", value)
