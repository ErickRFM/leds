package com.rgbcontroller.core.components.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.components.model.ComponentTelemetryCodec
import com.rgbcontroller.core.components.status.ComponentDashboardState
import com.rgbcontroller.core.components.status.ModuleEvidence
import com.rgbcontroller.core.components.telemetry.TelemetryChart
import java.util.Locale

/**
 * Real, model-scoped DHT telemetry. A GPIO lease is not sensor detection.
 * Ambient readings require the selected physical model to be active in
 * firmware; chart samples are already scoped by model + device.
 */
@Composable
fun SensorDashboard(
    id: String,
    state: ComponentDashboardState,
    active: Boolean,
    live: Boolean,
    onRefresh: () -> Unit
) {
    var minutes by rememberSaveable(id) { mutableIntStateOf(30) }
    var showDiagnostics by rememberSaveable(id) { mutableStateOf(false) }
    val readings = if (live) state.authoritativeTelemetry else state.persistedTelemetry
    fun metric(type: String): Float? {
        if (!active) return null
        return ComponentTelemetryCodec.find(readings.orEmpty(), type)
            ?.takeIf { it.valid && it.status == "fresh" && it.value.isFinite() }?.value
    }
    val temperature = metric("temperature")
    val humidity = metric("humidity")
    val physicalReading = active && live && ModuleEvidence.dhtResponding(state, id)
    val firmwareStatus = ComponentTelemetryCodec.find(readings.orEmpty(), "temperature")?.status
    val diagnostic = (if (live) state.authoritative else state.persisted)
        .orEmpty().firstOrNull { it.id == "sensor.$id" }
    val fault = diagnostic?.diagnostic
    val windowMs = minutes * 60_000L
    val now = System.currentTimeMillis()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Lecturas ${id.uppercase(Locale.ROOT)}",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (physicalReading) "En vivo" else if (!live) "No en vivo" else "Sin lectura",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SensorMetric("Temperatura", temperature, "°C", live,
                Modifier.weight(1f).testTag("sensor_temperature_$id"))
            SensorMetric("Humedad relativa", humidity, "%", live,
                Modifier.weight(1f).testTag("sensor_humidity_$id"))
        }
        OutlinedButton(onClick = { showDiagnostics = !showDiagnostics },
            modifier = Modifier.testTag("sensor_diagnostics_toggle_$id")) {
            Text(if (showDiagnostics) "Ocultar diagnóstico" else "Detalles y diagnóstico")
        }
        if (showDiagnostics) Card(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Diagnóstico de la lectura", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        !state.connected ->
                            "No se confirmó una conexión con telemetría ambiental."
                        !active -> "El driver ${id.uppercase()} no tiene un GPIO activo verificado."
                        state.authorityError != null ->
                            "No se confirmó la telemetría: ${state.authorityError}"
                        physicalReading ->
                            "Temperatura y humedad confirmadas por el ESP32."
                        fault == "line_low" ->
                            "DATA permanece en nivel bajo en GPIO${diagnostic?.pins?.firstOrNull() ?: "?"}. Revisa cortocircuito, GND y orden de pines."
                        fault == "no_response" ->
                            "GPIO no recibió el pulso de respuesta del ${id.uppercase()}. Revisa DATA, alimentación, pull-up y continuidad."
                        fault == "response_timeout" ->
                            "El sensor comenzó a responder pero no completó el pulso de sincronización."
                        fault == "bit_timeout" ->
                            "Llegó una respuesta incompleta o fuera de tiempo; verifica cableado y pull-up."
                        fault == "bad_checksum" ->
                            "Se recibieron 40 bits, pero el checksum no coincide: posibles interferencias o temporización."
                        fault == "invalid_payload" ->
                            "Checksum correcto, pero los valores no corresponden al modelo seleccionado."
                        firmwareStatus == "sensor_error" ->
                            "El controlador recibió respuestas inválidas repetidamente."
                        firmwareStatus == "stale" || firmwareStatus == "invalid" ->
                            "Lectura inválida: revisa modelo, alimentación, cable DATA y pull-up."
                        else ->
                            "GPIO asignado, pero el sensor aún no responde con una lectura válida."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (active && diagnostic?.failures != null && diagnostic.failures > 0) {
                    Text("Fallos de lectura consecutivos: ${diagnostic.failures}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (active && !physicalReading) {
                    Text("Verifica VCC → 3V3, GND → GND y DATA → GPIO configurado. " +
                        "Un DHT11 no debe leerse como DHT22.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!physicalReading) {
                    Text("GPIO asignado no significa sensor detectado físicamente.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = onRefresh, enabled = state.connected,
                    modifier = Modifier.testTag("sensor_refresh_$id")) {
                    Text("Actualizar lectura")
                }
            }
        }
        Text("Evolución en tiempo real", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (window in listOf(5, 15, 30)) {
                FilterChip(selected = minutes == window, onClick = { minutes = window },
                    label = { Text("$window min") },
                    modifier = Modifier.testTag("sensor_window_${window}_$id"))
            }
        }
        for ((type, title) in listOf(
            "temperature" to "Temperatura · °C",
            "humidity" to "Humedad relativa · %"
        )) {
            val geometry = TelemetryChart.build(
                componentId = id, measurementType = type,
                samples = state.series(id, type), nowMs = now, windowMs = windowMs,
                // Old valid measurements remain historical, not "live".
                staleAfterMs = windowMs, disconnected = !live
            )
            TelemetryChartCard(title, geometry, live = physicalReading,
                modifier = Modifier.testTag("sensor_history_${type}_$id"))
        }
    }
}

@Composable
private fun SensorMetric(
    title: String, value: Float?, unit: String,
    live: Boolean, modifier: Modifier
) {
    Card(modifier = modifier, shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )) {
        Column(Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value?.let { String.format(Locale.getDefault(), "%.1f %s", it, unit) }
                ?: "-- $unit", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold)
            Text(when {
                value == null -> "Sin lectura válida"
                live -> "● En vivo"
                else -> "Histórico"
            },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
