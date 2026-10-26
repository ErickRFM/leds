package com.rgbcontroller.core.voice

import kotlin.math.abs

/** Sensor transitions, never an initial reading or a stale/repeated packet. */
class EnvironmentalSpeechEngine(
    private val minimumIntervalMs: Long = 60_000L
) {
    enum class Cue(val message: String) {
        COLD("Ya hace frío."),
        HOT("Ya se siente calor."),
        COOLER("Ya está más fresco."),
        LIGHT_OFF("Se ha apagado la luz."),
        LIGHT_ON("Se ha encendido la luz.")
    }

    private var temperatureBand: Int? = null
    private var lightBand: Int? = null
    private var previousLight: Float? = null
    private var pendingLightBand: Int? = null
    private var pendingLightAt: Long = 0L
    private var lastTemperatureAt: Long = Long.MIN_VALUE
    private var lastLightAt: Long = Long.MIN_VALUE
    private var lastSampleAt: Long = Long.MIN_VALUE
    private var pendingTemperatureBand: Int? = null
    private var pendingTemperatureAt: Long = 0L

    fun reset() {
        temperatureBand = null
        lightBand = null
        previousLight = null
        pendingLightBand = null
        pendingLightAt = 0L
        lastTemperatureAt = Long.MIN_VALUE
        lastLightAt = Long.MIN_VALUE
        lastSampleAt = Long.MIN_VALUE
        pendingTemperatureBand = null
        pendingTemperatureAt = 0L
    }

    fun evaluate(
        temperatureC: Float?,
        lightPercent: Float?,
        sampleAtMs: Long,
        nowMs: Long
    ): List<Cue> {
        if (sampleAtMs <= lastSampleAt || sampleAtMs > nowMs + 5_000L ||
            nowMs - sampleAtMs > 10_000L) return emptyList()
        lastSampleAt = sampleAtMs
        val events = mutableListOf<Cue>()
        if (temperatureC != null && temperatureC.isFinite() && temperatureC in -40f..80f) {
            val previous = temperatureBand
            val next = when (previous) {
                0 -> if (temperatureC >= 20.5f) 1 else 0
                2 -> if (temperatureC < 25.5f) 1 else 2
                else -> when {
                    temperatureC < 19.5f -> 0
                    temperatureC >= 26.5f -> 2
                    else -> 1
                }
            }
            if (previous != null && next != previous && pendingTemperatureBand != next) {
                pendingTemperatureBand = next
                pendingTemperatureAt = nowMs
            }
            if (next == previous) pendingTemperatureBand = null
            if (previous != null && next != previous &&
                pendingTemperatureBand == next && nowMs - pendingTemperatureAt >= 2_000L &&
                due(lastTemperatureAt, nowMs)) {
                events += when (next) {
                    0 -> Cue.COLD
                    2 -> Cue.HOT
                    else -> Cue.COOLER
                }
                lastTemperatureAt = nowMs
                temperatureBand = next
                pendingTemperatureBand = null
            }
            if (previous == null) temperatureBand = next
        }
        if (lightPercent != null && lightPercent.isFinite() && lightPercent in 0f..100f) {
            val previous = previousLight
            // A large relative change plus a confirmed side of the light/dark
            // boundary; ordinary ADC noise alone must never trigger speech.
            val next = when (lightBand) {
                0 -> if (lightPercent >= 65f) 1 else 0
                1 -> if (lightPercent <= 25f) 0 else 1
                else -> if (lightPercent <= 25f) 0 else 1
            }
            if (lightBand != null && lightBand != next && pendingLightBand == null &&
                previous != null && abs(lightPercent - previous) >= 30f) {
                pendingLightBand = next
                pendingLightAt = nowMs
            }
            if (pendingLightBand != null && next != pendingLightBand) pendingLightBand = null
            if (lightBand != null && pendingLightBand == next &&
                nowMs - pendingLightAt >= 1_000L && due(lastLightAt, nowMs)) {
                events += if (next == 0) Cue.LIGHT_OFF else Cue.LIGHT_ON
                lastLightAt = nowMs
                lightBand = next
                pendingLightBand = null
            }
            if (lightBand == null) lightBand = next
            previousLight = lightPercent
        }
        return events
    }

    private fun due(previous: Long, now: Long) =
        previous == Long.MIN_VALUE || (now >= previous && now - previous >= minimumIntervalMs)
}
