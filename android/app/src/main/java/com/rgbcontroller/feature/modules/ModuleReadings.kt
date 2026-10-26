package com.rgbcontroller.feature.modules

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.components.model.ComponentMeasurement
import com.rgbcontroller.core.components.model.ComponentTelemetryCodec
import com.rgbcontroller.core.components.registry.ComponentCatalog
import com.rgbcontroller.core.components.status.ComponentDashboardState
import com.rgbcontroller.core.components.status.ComponentStatusFreshness
import com.rgbcontroller.core.components.telemetry.TelemetryChart
import com.rgbcontroller.core.components.widgets.SensorDashboard
import com.rgbcontroller.core.components.widgets.SoilMoistureWidget
import com.rgbcontroller.core.components.widgets.TelemetryChartCard

@Composable
internal fun ModuleReadings(
    id: String,
    state: ComponentDashboardState,
    onRefresh: () -> Unit,
    canCalibrateLdr: Boolean = false,
    calibrationMessage: String? = null,
    canControlOled: Boolean = false,
    oledActionMessage: String? = null,
    onOledFace: (String) -> Unit = {},
    onCalibrateLdr: (Int, Int) -> Unit = { _, _ -> }
) {
    val live = state.telemetryFreshness == ComponentStatusFreshness.LIVE &&
        state.authorityError == null && state.connected
    val reported = if (live) state.authoritative else state.persisted
    val active = reported.orEmpty().any {
        it.active && ComponentCatalog.catalogIdForFirmwareId(it.id) == id
    }
    val measurements = if (live) state.authoritativeTelemetry else state.persistedTelemetry
    fun reading(type: String): ComponentMeasurement? =
        ComponentTelemetryCodec.find(measurements.orEmpty(), type)
            ?.takeIf { it.valid && it.status == "fresh" }
    when (id) {
        "dht11", "dht22" -> SensorDashboard(id, state, active, live, onRefresh)
        "soil_moisture" -> {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SoilMoistureWidget(
                    moisturePercent = reading("soil_moisture")?.value,
                    rawAdc = reading("adc_raw")?.value?.toInt(),
                    calibrated = reading("soil_moisture") != null, live = live,
                    hasData = reading("soil_moisture") != null || reading("adc_raw") != null
                )
                val windowMs = TelemetryChart.DEFAULT_WINDOW_MS
                val geometry = TelemetryChart.build(
                    "soil_moisture", "soil_moisture",
                    state.series("soil_moisture", "soil_moisture"),
                    nowMs = System.currentTimeMillis(), windowMs = windowMs,
                    staleAfterMs = windowMs, disconnected = !live
                )
                TelemetryChartCard("Historial de humedad del suelo", geometry, live)
            }
        }
        "ldr_light" -> {
            var darkReference by rememberSaveable { mutableStateOf<Int?>(null) }
            var brightReference by rememberSaveable { mutableStateOf<Int?>(null) }
            var showCalibrationHelp by rememberSaveable { mutableStateOf(false) }
            val light = if (active && live) reading("ambient_light")?.value else null
            val raw = if (active && live) reading("ambient_light_raw")?.value?.toInt() else null
            val status = ComponentTelemetryCodec.find(measurements.orEmpty(), "ambient_light")?.status
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(Modifier.fillMaxWidth().testTag("ldr_light_readings")) {
                    Column(Modifier.padding(16.dp).animateContentSize(animationSpec = tween(250)),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Sensor de luminosidad", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("LDR · luz relativa medida por el ESP32", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (light != null) "%.0f %%".format(java.util.Locale.ROOT, light)
                                    else "— %",
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.testTag("ldr_light_percent")
                                )
                                Text(if (light != null) "Luz relativa calibrada" else if (!live) "Sin conexión en vivo" else "Sin lectura calibrada",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.Lightbulb, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp))
                        }
                        if (light != null) {
                            LinearProgressIndicator(
                                progress = { light.coerceIn(0f, 100f) / 100f },
                                modifier = Modifier.fillMaxWidth().height(9.dp)
                                    .testTag("ldr_calibrated_level")
                            )
                        }
                        Text(
                            when {
                                !live -> "Sin telemetría en vivo; no se presentan valores almacenados como actuales."
                                !active -> "El firmware no tiene un GPIO ADC1 activo para este módulo."
                                status == "not_calibrated" -> "ADC activo, falta calibrar referencias de oscuridad y claridad."
                                status == "sensor_error" || status == "invalid" -> "Lectura ADC inválida. Comprueba el divisor de 3.3 V y el cableado."
                                light != null -> "Porcentaje calibrado: es una escala relativa, no lux."
                                else -> "Esperando una lectura calibrada y válida."
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text("Lectura física ADC1: ${raw?.toString() ?: "—"} / 4095",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("ldr_light_raw"))
                        OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                            Text("Actualizar lectura del ESP32")
                        }
                        HorizontalDivider()
                        Text("Calibración de luz", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { showCalibrationHelp = !showCalibrationHelp },
                            modifier = Modifier.testTag("ldr_calibration_help")) {
                            Text(if (showCalibrationHelp) "Ocultar guía" else "¿Cómo calibrar?")
                        }
                        if (showCalibrationHelp) {
                            Text("Cubre el LDR, actualiza y captura oscuridad. Luego ilumínalo, " +
                                "actualiza y captura claridad. Aplica con BLE propietario o Wi-Fi autorizado.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (raw != null) {
                            LinearProgressIndicator(
                                progress = { raw.coerceIn(0, 4095) / 4095f },
                                modifier = Modifier.fillMaxWidth().testTag("ldr_adc_level")
                            )
                            Text("Señal ADC: $raw / 4095 · señal cruda, no porcentaje de luz",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { darkReference = raw },
                                enabled = canCalibrateLdr && raw != null,
                                modifier = Modifier.weight(1f).testTag("ldr_capture_dark")
                            ) { Text("Oscuridad") }
                            OutlinedButton(
                                onClick = { brightReference = raw },
                                enabled = canCalibrateLdr && raw != null,
                                modifier = Modifier.weight(1f).testTag("ldr_capture_bright")
                            ) { Text("Claridad") }
                        }
                        Text("Referencia oscura: ${darkReference ?: "—"} · clara: ${brightReference ?: "—"}",
                             style = MaterialTheme.typography.bodySmall)
                        val referenceSpan = if (darkReference != null && brightReference != null)
                            brightReference!! - darkReference!! else null
                        Text(
                            when {
                                referenceSpan == null -> "Actualiza y captura ambas referencias por separado."
                                referenceSpan <= 0 -> "Referencias iguales o invertidas: cambia la luz y vuelve a actualizar."
                                referenceSpan < 32 -> "Cambio ADC insuficiente (${referenceSpan}); se requieren al menos 32 puntos."
                                else -> "Separación válida: ${referenceSpan} puntos. Puedes aplicar la calibración."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (referenceSpan != null && referenceSpan < 32)
                                MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("ldr_calibration_guidance")
                        )
                        Button(
                            enabled = canCalibrateLdr && darkReference != null &&
                                brightReference != null &&
                                brightReference!! - darkReference!! >= 32,
                            onClick = {
                                val dark = darkReference
                                val bright = brightReference
                                if (dark != null && bright != null) onCalibrateLdr(dark, bright)
                            },
                            modifier = Modifier.fillMaxWidth().testTag("ldr_apply_calibration")
                        ) { Text("Aplicar calibración") }
                        if (calibrationMessage != null) {
                            Text(calibrationMessage, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                val windowMs = TelemetryChart.DEFAULT_WINDOW_MS
                val geometry = TelemetryChart.build(
                    "ldr_light", "ambient_light",
                    state.series("ldr_light", "ambient_light"),
                    nowMs = System.currentTimeMillis(), windowMs = windowMs,
                    staleAfterMs = windowMs, disconnected = !live
                )
                TelemetryChartCard("Historial de luz relativa", geometry, live)
            }
        }
        "ssd1306_oled" -> {
            // The display consumes the shared DHT11/LDR snapshots; it is not
            // itself a sensor. Never invent frames, measurements or audio input.
            val temp = if (live) reading("temperature")?.value else null
            val humidity = if (live) reading("humidity")?.value else null
            val light = if (live) reading("ambient_light")?.value else null
            val raw = if (live) reading("ambient_light_raw")?.value else null
            Card(Modifier.fillMaxWidth().testTag("oled_environment_dashboard")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Compi · Pantalla y ambiente", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (live && active) "OLED conectada · telemetría en vivo"
                        else "Sin confirmación actual de la pantalla y sus datos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF07111A))) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("•ᴗ•", style = MaterialTheme.typography.displayLarge,
                                color = Color(0xFF9EE6FF), modifier = Modifier.weight(1f))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(temp?.let { "%.1f °C".format(java.util.Locale.ROOT, it) } ?: "— °C",
                                    style = MaterialTheme.typography.titleLarge, color = Color(0xFF9EE6FF))
                                Text(humidity?.let { "%.0f %%".format(java.util.Locale.ROOT, it) } ?: "— %",
                                    style = MaterialTheme.typography.titleMedium, color = Color(0xFF9EE6FF))
                                Text(light?.let { "%.0f %% luz".format(java.util.Locale.ROOT, it) }
                                    ?: raw?.let { "%.0f ADC".format(java.util.Locale.ROOT, it) } ?: "— luz",
                                    style = MaterialTheme.typography.labelMedium, color = Color(0xFF9EE6FF))
                            }
                        }
                    }
                    Text("Expresiones físicas", style = MaterialTheme.typography.titleMedium)
                    if (oledActionMessage != null) {
                        Text(oledActionMessage, style = MaterialTheme.typography.bodySmall,
                            color = if (oledActionMessage.startsWith("OLED:")) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary)
                    }
                    if (!canControlOled) {
                        Text("Conecta y autoriza Bluetooth o Wi-Fi para cambiar expresiones.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((face, label) in listOf("happy" to "Feliz", "sleepy" to "Sueño")) {
                            OutlinedButton(onClick = { onOledFace(face) }, enabled = canControlOled,
                                modifier = Modifier.weight(1f).testTag("oled_face_$face")) { Text(label) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((face, label) in listOf("surprised" to "Sorpresa", "angry" to "Alerta")) {
                            OutlinedButton(onClick = { onOledFace(face) }, enabled = canControlOled,
                                modifier = Modifier.weight(1f).testTag("oled_face_$face")) { Text(label) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((face, label) in listOf("pumpkin" to "Calabaza",
                                "cat" to "Gato", "dance" to "Baile")) {
                            OutlinedButton(onClick = { onOledFace(face) }, enabled = canControlOled,
                                modifier = Modifier.weight(1f).testTag("oled_face_$face")) {
                                Text(label, maxLines = 1)
                            }
                        }
                    }
                    OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                        Text("Actualizar lecturas")
                    }
                }
            }
        }
        else -> Card(Modifier.fillMaxWidth()) {
            Text("Este componente todavía no reporta lecturas compatibles con la app.",
                modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

