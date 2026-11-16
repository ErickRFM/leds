package com.rgbcontroller.feature.modules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.rgbcontroller.core.components.model.ComponentMeasurement
import com.rgbcontroller.core.components.model.ComponentTelemetryCodec
import com.rgbcontroller.core.components.status.ComponentDashboardState
import com.rgbcontroller.core.components.status.ComponentStatusFreshness
import com.rgbcontroller.core.components.widgets.MetricReading
import com.rgbcontroller.core.ui.CompiTokens
import java.util.Locale

/**
 * The one reading a module is judged by in the list, so entering Modules shows
 * what every module is doing without opening each one.
 */
data class ModuleGlance(
    val label: String,
    val value: String,
    val unit: String,
    /** Real secondary values already confirmed, never placeholders turned into numbers. */
    val detail: String,
    val live: Boolean
) {
    val provenance: String get() = if (live) "● En vivo" else "No en vivo"
}

/**
 * Pure glance selection shared by the list and its tests. A value only appears
 * when the firmware measured it and marked it fresh; the app never derives a
 * number from a lease, a draft or a catalog entry.
 */
object ModuleGlanceSource {
    private const val MISSING = "—"

    fun forModule(
        id: String,
        state: ComponentDashboardState,
        climateModel: String? = null
    ): ModuleGlance? {
        val live = state.telemetryFreshness == ComponentStatusFreshness.LIVE &&
            state.authorityError == null && state.connected
        val measurements = if (live) state.authoritativeTelemetry else state.persistedTelemetry
        fun reading(type: String): ComponentMeasurement? =
            ComponentTelemetryCodec.find(measurements.orEmpty(), type)
                ?.takeIf { it.valid && it.status == "fresh" }
        val climateLabel = climateModel
            ?.let { "${it.uppercase(Locale.getDefault())} · Temperatura" }
            ?: "Temperatura"
        return when (id) {
            "dht11", "dht22" -> climate(reading("temperature"), reading("humidity"), live, climateLabel)
            "environmental_workspace" -> climate(
                temperature = reading("temperature"),
                humidity = reading("humidity"),
                live = live,
                label = climateLabel,
                extra = reading("soil_moisture")?.let { "Suelo ${format(it.value, 0)} %" }
            )
            "soil_moisture" -> {
                val percent = reading("soil_moisture")
                val raw = reading("adc_raw")
                when {
                    percent != null -> ModuleGlance(
                        label = "Humedad del suelo",
                        value = format(percent.value, 0),
                        unit = "%",
                        detail = raw?.let { "ADC ${format(it.value, 0)} / 4095" }
                            ?: "Sin lectura ADC1",
                        live = live
                    )
                    raw != null -> ModuleGlance(
                        label = "ADC del suelo · sin calibrar",
                        value = format(raw.value, 0),
                        unit = "/ 4095",
                        detail = "Calibra para obtener porcentaje",
                        live = live
                    )
                    else -> emptyGlance("Humedad del suelo", "%", live)
                }
            }
            "ldr_light" -> {
                val calibrated = reading("ambient_light")
                val raw = reading("ambient_light_raw")
                when {
                    calibrated != null -> ModuleGlance(
                        label = "Luz relativa",
                        value = format(calibrated.value, 0),
                        unit = "%",
                        detail = raw?.let { "ADC ${format(it.value, 0)} / 4095" } ?: "Sin lectura ADC cruda",
                        live = live
                    )
                    raw != null -> ModuleGlance(
                        label = "Luz cruda · sin calibrar",
                        value = format(raw.value, 0),
                        unit = "/ 4095",
                        detail = "Calibra para obtener porcentaje",
                        live = live
                    )
                    else -> emptyGlance("Luz relativa", "%", live)
                }
            }
            else -> null
        }
    }

    private fun climate(
        temperature: ComponentMeasurement?,
        humidity: ComponentMeasurement?,
        live: Boolean,
        label: String = "Temperatura",
        extra: String? = null
    ): ModuleGlance {
        val parts = listOfNotNull(
            humidity?.let { "Humedad ${format(it.value, 0)} %" },
            extra
        )
        return ModuleGlance(
            label = label,
            value = temperature?.let { format(it.value, 1) } ?: MISSING,
            unit = "°C",
            detail = parts.joinToString(" · ").ifBlank { "Sin lecturas válidas" },
            live = live
        )
    }

    private fun emptyGlance(label: String, unit: String, live: Boolean) = ModuleGlance(
        label = label,
        value = MISSING,
        unit = unit,
        detail = if (live) "Sin lectura válida" else "Sin confirmación del firmware",
        live = live
    )

    private fun format(value: Float, decimals: Int): String =
        String.format(Locale.getDefault(), "%.${decimals}f", value)
}

/**
 * The list's one-line reading for a module, in the same grammar as the sensor
 * cards: caption, real value, unit, then where the value came from.
 */
@Composable
internal fun ModuleGlanceRow(glance: ModuleGlance, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(CompiTokens.Spacing.s)
    ) {
        MetricReading(
            label = glance.label,
            value = glance.value,
            unit = glance.unit
        )
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CompiTokens.Spacing.xxs)
        ) {
            Text(
                glance.provenance,
                style = CompiTokens.Typography.MetricCaption,
                color = if (glance.live) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                glance.detail,
                style = CompiTokens.Typography.MetricUnit,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
