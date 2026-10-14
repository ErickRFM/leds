package com.rgbcontroller.core.components.model

import org.json.JSONObject

/**
 * One firmware-reported measurement. [valid] is only true when the firmware
 * flagged the reading valid AND the numeric value is finite; the Android side
 * never upgrades an invalid/stale reading to "real data".
 */
data class ComponentMeasurement(
    val type: String,
    val status: String,
    val valid: Boolean,
    val value: Float,
    val unit: String,
    val timestampMs: Long
)

/**
 * Strict, bounded decoder for the firmware's read-only telemetry contract
 * (`{"version":1,"measurements":[...]}` emitted by SensorTelemetryCodec).
 * Returns null when the payload is malformed so callers can distinguish
 * "no data" from a genuine empty (but valid) list.
 */
object ComponentTelemetryCodec {
    const val MAX_MEASUREMENTS = 12
    // Mirrors esp32core::MeasurementStatus names emitted by SensorTelemetryCodec.
    private val KNOWN_STATUSES = setOf(
        "absent", "fresh", "stale", "invalid", "not_calibrated", "sensor_error"
    )

    fun decode(raw: String?): List<ComponentMeasurement>? {
        if (raw.isNullOrBlank()) return null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (json.optInt("version", -1) != 1) return null
        val array = json.optJSONArray("measurements") ?: return null
        val limit = minOf(array.length(), MAX_MEASUREMENTS)
        val out = ArrayList<ComponentMeasurement>(limit)
        for (i in 0 until limit) {
            val entry = array.optJSONObject(i) ?: continue
            val type = entry.optString("type", "")
            if (type.isBlank()) continue
            val status = entry.optString("status", "invalid")
            if (status !in KNOWN_STATUSES) continue
            val rawValue = entry.optDouble("value", Double.NaN)
            val finite = !rawValue.isNaN() && !rawValue.isInfinite()
            val value = if (finite) rawValue.toFloat() else Float.NaN
            out += ComponentMeasurement(
                type = type,
                status = status,
                valid = entry.optBoolean("valid", false) && finite,
                value = value,
                unit = entry.optString("unit", ""),
                timestampMs = entry.optLong("timestampMs", 0L).coerceAtLeast(0L)
            )
        }
        return out
    }

    fun find(measurements: List<ComponentMeasurement>, type: String): ComponentMeasurement? =
        measurements.lastOrNull { it.type == type }

    /**
     * Maps firmware telemetry onto the UI component state. A component is only
     * ACTIVE when it reported at least one valid measurement; otherwise the
     * state reflects the reported problem instead of pretending to work.
     */
    fun stateFor(
        componentId: String,
        measurements: List<ComponentMeasurement>?
    ): ComponentState {
        if (measurements == null) return ComponentState.NO_DATA
        val types = measurementTypesFor(componentId)
        val relevant = measurements.filter { it.type in types }
        if (relevant.isEmpty()) return ComponentState.NO_DATA
        if (relevant.any { it.status == "sensor_error" || it.status == "invalid" }) {
            return ComponentState.ERROR
        }
        return when {
            relevant.any { it.valid } -> ComponentState.ACTIVE
            relevant.any { it.status == "not_calibrated" } -> ComponentState.NO_DATA
            else -> ComponentState.NO_DATA
        }
    }

    fun measurementTypesFor(componentId: String): Set<String> = when (componentId) {
        "dht22" -> setOf("humidity", "temperature")
        "soil_moisture" -> setOf("soil_moisture", "adc_raw")
        "led_rgb" -> setOf("rgb_state")
        else -> emptySet()
    }
}
