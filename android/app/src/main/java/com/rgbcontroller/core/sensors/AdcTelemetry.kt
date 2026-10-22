package com.rgbcontroller.core.sensors

data class AdcSample(val elapsedMs: Long, val raw: Int)

/** Foreground-only, memory-bounded ADC1 trace; values are raw and uncalibrated. */
object AdcTelemetry {
    const val MAX_SAMPLES = 120
    const val ADC12_MAX = 4095

    fun append(existing: List<AdcSample>, sample: AdcSample): List<AdcSample> {
        require(sample.elapsedMs >= 0)
        require(sample.raw in 0..ADC12_MAX)
        require(existing.isEmpty() || sample.elapsedMs >= existing.last().elapsedMs)
        return (existing + sample).takeLast(MAX_SAMPLES)
    }

    fun csv(samples: List<AdcSample>): String =
        "elapsed_ms,adc1_raw\n" +
            samples.joinToString("\n") { it.elapsedMs.toString() + "," + it.raw }
}
