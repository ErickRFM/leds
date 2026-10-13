package com.rgbcontroller.core.modules

import com.rgbcontroller.core.programs.LabRgbScriptPolicy
import com.rgbcontroller.core.programs.ProgramLanguage

/** Pre-flash suggestions only. Never invent installed hardware from source text. */
object ProjectModuleHints {
    private const val MAX_SOURCE = 64 * 1024

    fun suggestions(source: String, language: ProgramLanguage, engine: String): List<String> {
        if (source.isBlank() || source.length > MAX_SOURCE) return emptyList()
        if (language == ProgramLanguage.LAB_SCRIPT) {
            return if (engine == "RGB" && LabRgbScriptPolicy.validate(source).accepted)
                listOf("LED RGB · sugerencia del editor; verifica firmware") else emptyList()
        }
        // Explicit first-party annotations only. Arbitrary analogRead() could be
        // a potentiometer, light sensor, voltage divider or anything else.
        val annotations = source.lineSequence().map { it.trim() }.toSet()
        return buildList {
            if ("// @compi-module led.rgb.main" in annotations)
                add("LED RGB · anotación declarativa no verificada")
            if ("// @compi-module input.adc1.34" in annotations)
                add("Entrada ADC1 GPIO34 · sensor físico no verificado")
        }
    }
}
