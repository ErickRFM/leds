package com.rgbcontroller.core.lab

/** Bounded experimental on/off automation only; not background arbitrary code. */
object RgbAutomationPolicy {
    const val MAX_CYCLES = 10
    const val MAX_INTERVAL_SECONDS = 60
    const val MAX_DURATION_SECONDS = 1200
    fun valid(intervalSeconds: Int, cycles: Int): Boolean =
        intervalSeconds in 1..MAX_INTERVAL_SECONDS && cycles in 1..MAX_CYCLES &&
            intervalSeconds * 2 * cycles <= MAX_DURATION_SECONDS
    fun durationSeconds(intervalSeconds: Int, cycles: Int): Int {
        require(valid(intervalSeconds, cycles)) { "Automatización fuera de límites." }
        return intervalSeconds * cycles * 2
    }
}
