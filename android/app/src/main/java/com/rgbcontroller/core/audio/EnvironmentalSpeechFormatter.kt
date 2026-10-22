package com.rgbcontroller.core.audio

import com.rgbcontroller.core.components.model.ComponentMeasurement
import java.util.Locale

/** Reads only firmware-confirmed fresh values, not cached samples or raw ADC. */
object EnvironmentalSpeechFormatter {
    data class Report(val text: String, val hasFreshData: Boolean)

    fun report(
        measurements: List<ComponentMeasurement>,
        includeDht: Boolean,
        includeLight: Boolean
    ): Report {
        fun fresh(type: String, min: Float, max: Float): Float? =
            measurements.lastOrNull {
                it.type == type && it.status == "fresh" && it.valid &&
                    it.value.isFinite() && it.value in min..max
            }?.value

        val details = mutableListOf<String>()
        if (includeDht) {
            fresh("temperature", -40f, 85f)?.let {
                details += "La temperatura es de ${decimal(it)} grados Celsius."
            }
            fresh("humidity", 0f, 100f)?.let {
                details += "La humedad relativa es de ${decimal(it)} por ciento."
            }
        }
        if (includeLight) {
            fresh("ambient_light", 0f, 100f)?.let {
                details += "La iluminación relativa es de ${decimal(it)} por ciento."
            }
        }
        return if (details.isEmpty()) {
            Report("No hay mediciones ambientales recientes y válidas para anunciar.", false)
        } else {
            Report(details.joinToString(" "), true)
        }
    }

    private fun decimal(value: Float): String =
        String.format(Locale.forLanguageTag("es-MX"), "%.1f", value)
}
