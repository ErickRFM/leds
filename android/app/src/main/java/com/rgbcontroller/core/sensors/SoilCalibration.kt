package com.rgbcontroller.core.sensors

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Optional on-phone calibration for a soil-moisture probe on ADC1 GPIO34.
 * Never interpret a raw reading as a moisture percentage without both references.
 * This object does not establish a sensor's physical presence.
 */
data class SoilCalibration(val dryRaw: Int, val wetRaw: Int) {
    init {
        require(dryRaw in 0..4095 && wetRaw in 0..4095) { "ADC fuera de rango" }
        require(abs(dryRaw - wetRaw) >= 100) { "Referencias seco/húmedo demasiado cercanas" }
    }

    fun percentage(raw: Int): Int {
        require(raw in 0..4095)
        return (((raw - dryRaw).toDouble() / (wetRaw - dryRaw)) * 100.0)
            .roundToInt().coerceIn(0, 100)
    }
}
