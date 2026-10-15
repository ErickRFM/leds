package com.rgbcontroller.core.components.alerts

import com.rgbcontroller.core.components.telemetry.TelemetrySample
import org.json.JSONArray
import org.json.JSONObject

enum class AlertComparison { ABOVE, BELOW }

enum class AlertSeverity { INFO, WARNING, CRITICAL }

/** A user-configurable threshold rule over one telemetry series. */
data class AlertRule(
    val id: String,
    val componentId: String,
    val measurementType: String,
    val comparison: AlertComparison,
    val threshold: Float,
    val hysteresis: Float = 0f,
    val minIntervalMs: Long = 60_000L,
    val severity: AlertSeverity = AlertSeverity.WARNING,
    val enabled: Boolean = true
)

/** Per-rule runtime state; [lastRaisedAtMs] drives the rate limit. */
data class AlertState(
    val active: Boolean = false,
    val lastRaisedAtMs: Long = 0L,
    val lastValue: Float? = null
)

/** A raise ([clear] false) or clear ([clear] true) transition of one rule. */
data class AlertEvent(
    val ruleId: String,
    val componentId: String,
    val measurementType: String,
    val severity: AlertSeverity,
    val comparison: AlertComparison,
    val threshold: Float,
    val value: Float,
    val clear: Boolean,
    val atMs: Long
)

data class AlertEvaluation(
    val states: Map<String, AlertState>,
    val events: List<AlertEvent>
)

/**
 * Pure, deterministic alert state machine. It never alerts on an invalid, stale
 * or missing reading; it uses hysteresis so a value hovering at the threshold
 * does not flap; and it enforces a minimum interval between raises and never
 * repeats a raise while a rule is already active.
 */
object AlertEngine {
    fun key(componentId: String, measurementType: String): String =
        "$componentId\u0001$measurementType"

    /** Latest plottable sample per series, newest wins. */
    fun latestBySeries(samples: List<TelemetrySample>): Map<String, TelemetrySample> {
        val out = HashMap<String, TelemetrySample>()
        for (sample in samples) {
            if (!sample.isPlottable) continue
            val k = key(sample.componentId, sample.measurementType)
            val existing = out[k]
            if (existing == null || sample.recordedAtMs >= existing.recordedAtMs) out[k] = sample
        }
        return out
    }

    fun evaluate(
        rules: List<AlertRule>,
        latest: Map<String, TelemetrySample>,
        previous: Map<String, AlertState>,
        nowMs: Long
    ): AlertEvaluation {
        val states = previous.toMutableMap()
        val events = ArrayList<AlertEvent>()
        for (rule in rules) {
            if (!rule.enabled) continue
            val sample = latest[key(rule.componentId, rule.measurementType)] ?: continue
            val value = sample.value ?: continue
            val prev = previous[rule.id] ?: AlertState()
            val tripped = when (rule.comparison) {
                AlertComparison.ABOVE -> value >= rule.threshold
                AlertComparison.BELOW -> value <= rule.threshold
            }
            val released = when (rule.comparison) {
                AlertComparison.ABOVE -> value <= rule.threshold - rule.hysteresis
                AlertComparison.BELOW -> value >= rule.threshold + rule.hysteresis
            }
            when {
                prev.active && released -> {
                    states[rule.id] = prev.copy(active = false, lastValue = value)
                    events += event(rule, value, clear = true, atMs = nowMs)
                }
                !prev.active && tripped &&
                    (prev.lastRaisedAtMs <= 0L || nowMs - prev.lastRaisedAtMs >= rule.minIntervalMs) -> {
                    states[rule.id] = AlertState(active = true, lastRaisedAtMs = nowMs, lastValue = value)
                    events += event(rule, value, clear = false, atMs = nowMs)
                }
                prev.active -> {
                    states[rule.id] = prev.copy(lastValue = value)
                }
            }
        }
        return AlertEvaluation(states, events)
    }

    private fun event(rule: AlertRule, value: Float, clear: Boolean, atMs: Long) = AlertEvent(
        ruleId = rule.id,
        componentId = rule.componentId,
        measurementType = rule.measurementType,
        severity = rule.severity,
        comparison = rule.comparison,
        threshold = rule.threshold,
        value = value,
        clear = clear,
        atMs = atMs
    )
}

/**
 * Thresholds that ship with the app. They are ordinary [AlertRule]s: the user
 * can change them, and none of them is treated as accepted by the firmware.
 */
object DefaultAlertRules {
    fun defaults(): List<AlertRule> = listOf(
        AlertRule("dht22_temp_high", "dht22", "temperature", AlertComparison.ABOVE, 35f,
            hysteresis = 1f, severity = AlertSeverity.WARNING),
        AlertRule("dht22_temp_critical", "dht22", "temperature", AlertComparison.ABOVE, 45f,
            hysteresis = 1f, severity = AlertSeverity.CRITICAL),
        AlertRule("dht22_humidity_low", "dht22", "humidity", AlertComparison.BELOW, 30f,
            hysteresis = 2f, severity = AlertSeverity.WARNING),
        AlertRule("soil_moisture_low", "soil_moisture", "soil_moisture", AlertComparison.BELOW, 25f,
            hysteresis = 2f, severity = AlertSeverity.WARNING)
    )
}

/** Versioned codec for persisted alert rules. Never stores credentials. */
object AlertRulesCodec {
    const val VERSION = 1

    fun encode(rules: List<AlertRule>): String {
        val root = JSONObject()
        root.put("version", VERSION)
        val array = JSONArray()
        for (rule in rules) {
            val entry = JSONObject()
            entry.put("id", rule.id)
            entry.put("componentId", rule.componentId)
            entry.put("measurementType", rule.measurementType)
            entry.put("comparison", rule.comparison.name)
            entry.put("threshold", rule.threshold.toDouble())
            entry.put("hysteresis", rule.hysteresis.toDouble())
            entry.put("minIntervalMs", rule.minIntervalMs)
            entry.put("severity", rule.severity.name)
            entry.put("enabled", rule.enabled)
            array.put(entry)
        }
        root.put("rules", array)
        return root.toString()
    }

    fun decode(raw: String?): List<AlertRule> {
        if (raw.isNullOrBlank()) return emptyList()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        if (root.optInt("version", -1) != VERSION) return emptyList()
        val array = root.optJSONArray("rules") ?: return emptyList()
        val out = ArrayList<AlertRule>(array.length())
        for (i in 0 until array.length()) {
            val entry = array.optJSONObject(i) ?: continue
            val id = entry.optString("id", "")
            if (id.isBlank()) continue
            val comparison = runCatching {
                AlertComparison.valueOf(entry.optString("comparison", AlertComparison.ABOVE.name))
            }.getOrDefault(AlertComparison.ABOVE)
            val severity = runCatching {
                AlertSeverity.valueOf(entry.optString("severity", AlertSeverity.WARNING.name))
            }.getOrDefault(AlertSeverity.WARNING)
            out += AlertRule(
                id = id,
                componentId = entry.optString("componentId", ""),
                measurementType = entry.optString("measurementType", ""),
                comparison = comparison,
                threshold = entry.optDouble("threshold", 0.0).toFloat(),
                hysteresis = entry.optDouble("hysteresis", 0.0).toFloat(),
                minIntervalMs = entry.optLong("minIntervalMs", 60_000L).coerceAtLeast(0L),
                severity = severity,
                enabled = entry.optBoolean("enabled", true)
            )
        }
        return out
    }
}
