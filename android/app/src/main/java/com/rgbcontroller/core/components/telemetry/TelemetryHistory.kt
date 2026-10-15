package com.rgbcontroller.core.components.telemetry

import com.rgbcontroller.core.components.model.ComponentMeasurement
import org.json.JSONArray
import org.json.JSONObject

/** Where a history sample came from: a live firmware report or a stored snapshot. */
enum class TelemetryProvenance { FIRMWARE, LAST_KNOWN }

/**
 * One point in the local telemetry history. A firmware [ComponentMeasurement]
 * says what a sensor reported; a [TelemetrySample] adds the identity that the
 * measurement alone does not carry: the device, the catalog component and the
 * moment the app recorded it. A sample never fabricates a value for an
 * invalid/absent reading: [value] stays null and [isPlottable] stays false.
 */
data class TelemetrySample(
    val deviceId: String,
    val componentId: String,
    val measurementType: String,
    val value: Float?,
    val unit: String,
    val status: String,
    val valid: Boolean,
    val sourceTimestampMs: Long,
    val recordedAtMs: Long,
    val provenance: TelemetryProvenance
) {
    /** Only a firmware-validated finite value may ever feed a chart. */
    val isPlottable: Boolean get() = valid && value != null && value.isFinite()
}

/** Bounded per-series retention so the on-device history can never grow forever. */
data class TelemetryHistoryLimits(
    val maxSamplesPerSeries: Int = 240,
    val maxSeries: Int = 12,
    val maxTotalSamples: Int = 2000,
    val retentionMs: Long = 6 * 60 * 60 * 1000L
)

/**
 * Pure, deterministic history algebra shared by the repository and its tests.
 * A "series" is one device + component + measurement type; `append` trims each
 * series by count, drops samples past the retention window and caps the number
 * of live series so a noisy bus cannot exhaust memory or storage.
 */
object TelemetryHistory {
    fun seriesKey(sample: TelemetrySample): String =
        "${sample.deviceId}\u0001${sample.componentId}\u0001${sample.measurementType}"

    /** History for one device only; another device's samples never leak through. */
    fun samplesForDevice(samples: List<TelemetrySample>, deviceId: String?): List<TelemetrySample> =
        if (deviceId == null) emptyList() else samples.filter { it.deviceId == deviceId }

    fun samplesForSeries(
        samples: List<TelemetrySample>,
        componentId: String,
        measurementType: String
    ): List<TelemetrySample> =
        samples.filter { it.componentId == componentId && it.measurementType == measurementType }

    fun append(
        existing: List<TelemetrySample>,
        incoming: List<TelemetrySample>,
        limits: TelemetryHistoryLimits = TelemetryHistoryLimits(),
        nowMs: Long
    ): List<TelemetrySample> {
        val retained = (existing + incoming).filter { nowMs - it.recordedAtMs <= limits.retentionMs }
        val bySeries = LinkedHashMap<String, MutableList<TelemetrySample>>()
        for (sample in retained) {
            bySeries.getOrPut(seriesKey(sample)) { ArrayList() }.add(sample)
        }
        val ordered = bySeries.entries
            .sortedByDescending { entry -> entry.value.maxOf { it.recordedAtMs } }
            .take(limits.maxSeries)
        val pruned = ArrayList<TelemetrySample>()
        for (entry in ordered) {
            val series = entry.value.sortedBy { it.recordedAtMs }
            val keep = if (series.size > limits.maxSamplesPerSeries)
                series.subList(series.size - limits.maxSamplesPerSeries, series.size)
            else series
            pruned += keep
        }
        val ordered2 = pruned.sortedBy { it.recordedAtMs }
        return if (ordered2.size > limits.maxTotalSamples)
            ordered2.subList(ordered2.size - limits.maxTotalSamples, ordered2.size)
        else ordered2
    }

    /** Build history samples from decoded firmware measurements, tagged with identity. */
    fun samplesFromMeasurements(
        measurements: List<ComponentMeasurement>,
        deviceId: String,
        recordedAtMs: Long,
        provenance: TelemetryProvenance
    ): List<TelemetrySample> = measurements.map { measurement ->
        TelemetrySample(
            deviceId = deviceId,
            componentId = componentIdForMeasurementType(measurement.type),
            measurementType = measurement.type,
            // An invalid reading keeps no numeric value: a chart or consumer can
            // never mistake a stale/invalid sample for a real measurement.
            value = measurement.value.takeIf { measurement.valid && it.isFinite() },
            unit = measurement.unit,
            status = measurement.status,
            valid = measurement.valid,
            sourceTimestampMs = measurement.timestampMs,
            recordedAtMs = recordedAtMs,
            provenance = provenance
        )
    }

    /** Catalog component a firmware measurement type belongs to; never guesses. */
    fun componentIdForMeasurementType(type: String): String = when (type) {
        "humidity", "temperature" -> "dht22"
        "soil_moisture", "adc_raw" -> "soil_moisture"
        "rgb_state" -> "led_rgb"
        else -> "unknown"
    }
}

/**
 * Versioned, bounded codec for the local telemetry history. Only already-public
 * fields (identity, type/status/valid/value/unit/timestamps) are stored; a
 * non-finite value is persisted as null so a document can never resurrect a
 * fabricated number. Credentials and BLE keys are never written here.
 */
object TelemetryHistoryCodec {
    const val VERSION = 1
    private const val MAX_DECODED = 2000

    fun encode(samples: List<TelemetrySample>): String {
        val root = JSONObject()
        root.put("version", VERSION)
        val array = JSONArray()
        for (s in samples.take(MAX_DECODED)) {
            val entry = JSONObject()
            entry.put("deviceId", s.deviceId)
            entry.put("componentId", s.componentId)
            entry.put("type", s.measurementType)
            entry.put("value", s.value?.takeIf { it.isFinite() }?.toDouble() ?: JSONObject.NULL)
            entry.put("unit", s.unit)
            entry.put("status", s.status)
            entry.put("valid", s.valid && s.value?.isFinite() == true)
            entry.put("sourceTimestampMs", s.sourceTimestampMs)
            entry.put("recordedAtMs", s.recordedAtMs)
            entry.put("provenance", s.provenance.name)
            array.put(entry)
        }
        root.put("samples", array)
        return root.toString()
    }

    fun decode(raw: String?): List<TelemetrySample> {
        if (raw.isNullOrBlank()) return emptyList()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        if (root.optInt("version", -1) != VERSION) return emptyList()
        val array = root.optJSONArray("samples") ?: return emptyList()
        val limit = minOf(array.length(), MAX_DECODED)
        val out = ArrayList<TelemetrySample>(limit)
        for (i in 0 until limit) {
            val entry = array.optJSONObject(i) ?: continue
            val deviceId = entry.optString("deviceId", "")
            val type = entry.optString("type", "")
            if (deviceId.isBlank() || type.isBlank()) continue
            val rawValue = entry.optDouble("value", Double.NaN)
            val finite = !rawValue.isNaN() && !rawValue.isInfinite()
            val provenance = runCatching {
                TelemetryProvenance.valueOf(entry.optString("provenance", TelemetryProvenance.FIRMWARE.name))
            }.getOrDefault(TelemetryProvenance.FIRMWARE)
            out += TelemetrySample(
                deviceId = deviceId,
                componentId = entry.optString("componentId", "")
                    .ifBlank { componentIdForMeasurementType(type) },
                measurementType = type,
                value = if (finite) rawValue.toFloat() else null,
                unit = entry.optString("unit", ""),
                status = entry.optString("status", "invalid"),
                valid = entry.optBoolean("valid", false) && finite,
                sourceTimestampMs = entry.optLong("sourceTimestampMs", 0L).coerceAtLeast(0L),
                recordedAtMs = entry.optLong("recordedAtMs", 0L).coerceAtLeast(0L),
                provenance = provenance
            )
        }
        return out
    }

    private fun componentIdForMeasurementType(type: String): String =
        TelemetryHistory.componentIdForMeasurementType(type)
}
