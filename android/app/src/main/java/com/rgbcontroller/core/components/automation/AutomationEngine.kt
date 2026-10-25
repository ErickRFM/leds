package com.rgbcontroller.core.components.automation

import com.rgbcontroller.core.components.alerts.AlertComparison
import com.rgbcontroller.core.components.alerts.AlertEngine
import com.rgbcontroller.core.components.alerts.AlertRule
import com.rgbcontroller.core.components.alerts.AlertSeverity
import com.rgbcontroller.core.components.alerts.AlertState
import com.rgbcontroller.core.components.telemetry.TelemetrySample
import org.json.JSONArray
import org.json.JSONObject

/** Declarative sensor -> condition -> action. Hardware actions are not notices. */
sealed interface AutomationAction {
    val label: String

    data class Notify(
        val message: String,
        val severity: AlertSeverity = AlertSeverity.INFO
    ) : AutomationAction {
        override val label: String get() = "aviso"
    }

    data class SetRgb(
        val red: Int,
        val green: Int,
        val blue: Int,
        val brightness: Int
    ) : AutomationAction {
        override val label: String get() = "led_rgb"
    }

    data class UpdateOled(val text: String) : AutomationAction {
        override val label: String get() = "oled"
    }
}

/** A rule: when a telemetry series trips a threshold, run one [action]. */
data class AutomationRule(
    val id: String,
    val componentId: String,
    val measurementType: String,
    val comparison: AlertComparison,
    val threshold: Float,
    val hysteresis: Float = 0f,
    val minIntervalMs: Long = 60_000L,
    val action: AutomationAction,
    val enabled: Boolean = false
) {
    /** Hardware actions must pass through the transport's own authorization. */
    val isHardware: Boolean get() = action !is AutomationAction.Notify

    fun toAlertRule(): AlertRule = AlertRule(
        id = id,
        componentId = componentId,
        measurementType = measurementType,
        comparison = comparison,
        threshold = threshold,
        hysteresis = hysteresis,
        minIntervalMs = minIntervalMs,
        severity = AlertSeverity.INFO,
        enabled = enabled
    )
}

enum class AutomationEventStatus { NOTIFIED, PENDING, DISPATCHED, BLOCKED, FAILED }

data class AutomationEvent(
    val id: String,
    val ruleId: String,
    val componentId: String,
    val measurementType: String,
    val action: AutomationAction,
    val value: Float,
    val atMs: Long,
    val status: AutomationEventStatus
)

data class AutomationOutcome(
    val states: Map<String, AlertState>,
    val events: List<AutomationEvent>
)

/**
 * Pure automation engine. It reuses [AlertEngine] for the condition half
 * (hysteresis, minimum interval, no repeat while active, never fires on invalid
 * readings) and only decides *what* to do: it never sends anything. A NOTIFY is
 * recorded inline; a hardware action is left PENDING for the coordinator, which
 * must go through the existing command path.
 */
object AutomationEngine {
    fun evaluate(
        rules: List<AutomationRule>,
        latest: Map<String, TelemetrySample>,
        previous: Map<String, AlertState>,
        nowMs: Long
    ): AutomationOutcome {
        val evaluation = AlertEngine.evaluate(rules.map { it.toAlertRule() }, latest, previous, nowMs)
        val byId = rules.associateBy { it.id }
        val events = evaluation.events
            .filter { !it.clear }
            .mapNotNull { event ->
                val rule = byId[event.ruleId] ?: return@mapNotNull null
                AutomationEvent(
                    id = "${rule.id}@${event.atMs}",
                    ruleId = rule.id,
                    componentId = rule.componentId,
                    measurementType = rule.measurementType,
                    action = rule.action,
                    value = event.value,
                    atMs = event.atMs,
                    status = if (rule.isHardware) AutomationEventStatus.PENDING
                    else AutomationEventStatus.NOTIFIED
                )
            }
        return AutomationOutcome(evaluation.states, events)
    }
}

/**
 * Ships two safe, notice-only automations enabled by default. Any action that
 * touches hardware (RGB, OLED) or outputs stays disabled until the user opts in.
 */
object DefaultAutomationRules {
    fun defaults(): List<AutomationRule> = listOf(
        AutomationRule(
            "auto_temp_critical_notice", "dht22", "temperature",
            AlertComparison.ABOVE, 45f, hysteresis = 1f,
            action = AutomationAction.Notify("Temperatura crítica", AlertSeverity.CRITICAL),
            enabled = true
        ),
        // DHT11 is Compi's default physical probe (GPIO16). Only the active
        // sensor series can trigger, so supporting both models is not duplication.
        AutomationRule(
            "auto_temp_critical_notice_dht11", "dht11", "temperature",
            AlertComparison.ABOVE, 45f, hysteresis = 1f,
            action = AutomationAction.Notify("Temperatura crítica", AlertSeverity.CRITICAL),
            enabled = true
        ),
        AutomationRule(
            "auto_soil_dry_notice", "soil_moisture", "soil_moisture",
            AlertComparison.BELOW, 20f, hysteresis = 2f,
            action = AutomationAction.Notify("Suelo seco", AlertSeverity.WARNING),
            enabled = true
        )
    )
}

/** Versioned codec for persisted automation rules. Never stores credentials. */
object AutomationRulesCodec {
    const val VERSION = 1

    fun encode(rules: List<AutomationRule>): String {
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
            entry.put("enabled", rule.enabled)
            entry.put("action", encodeAction(rule.action))
            array.put(entry)
        }
        root.put("rules", array)
        return root.toString()
    }

    fun decode(raw: String?): List<AutomationRule> {
        if (raw.isNullOrBlank()) return emptyList()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        if (root.optInt("version", -1) != VERSION) return emptyList()
        val array = root.optJSONArray("rules") ?: return emptyList()
        val out = ArrayList<AutomationRule>(array.length())
        for (i in 0 until array.length()) {
            val entry = array.optJSONObject(i) ?: continue
            val id = entry.optString("id", "")
            if (id.isBlank()) continue
            val action = decodeAction(entry.optJSONObject("action")) ?: continue
            val comparison = runCatching {
                AlertComparison.valueOf(entry.optString("comparison", AlertComparison.ABOVE.name))
            }.getOrDefault(AlertComparison.ABOVE)
            out += AutomationRule(
                id = id,
                componentId = entry.optString("componentId", ""),
                measurementType = entry.optString("measurementType", ""),
                comparison = comparison,
                threshold = entry.optDouble("threshold", 0.0).toFloat(),
                hysteresis = entry.optDouble("hysteresis", 0.0).toFloat(),
                minIntervalMs = entry.optLong("minIntervalMs", 60_000L).coerceAtLeast(0L),
                action = action,
                enabled = entry.optBoolean("enabled", false)
            )
        }
        return out
    }

    private fun encodeAction(action: AutomationAction): JSONObject {
        val json = JSONObject()
        when (action) {
            is AutomationAction.Notify -> {
                json.put("type", "notify")
                json.put("message", action.message)
                json.put("severity", action.severity.name)
            }
            is AutomationAction.SetRgb -> {
                json.put("type", "set_rgb")
                json.put("red", action.red)
                json.put("green", action.green)
                json.put("blue", action.blue)
                json.put("brightness", action.brightness)
            }
            is AutomationAction.UpdateOled -> {
                json.put("type", "update_oled")
                json.put("text", action.text)
            }
        }
        return json
    }

    private fun decodeAction(json: JSONObject?): AutomationAction? {
        if (json == null) return null
        return when (json.optString("type", "")) {
            "notify" -> {
                val severity = runCatching {
                    AlertSeverity.valueOf(json.optString("severity", AlertSeverity.INFO.name))
                }.getOrDefault(AlertSeverity.INFO)
                AutomationAction.Notify(json.optString("message", ""), severity)
            }
            "set_rgb" -> AutomationAction.SetRgb(
                red = json.optInt("red", 0).coerceIn(0, 255),
                green = json.optInt("green", 0).coerceIn(0, 255),
                blue = json.optInt("blue", 0).coerceIn(0, 255),
                brightness = json.optInt("brightness", 100).coerceIn(0, 100)
            )
            "update_oled" -> AutomationAction.UpdateOled(json.optString("text", ""))
            else -> null
        }
    }
}

/** Transport seam: the existing command path (BLE queue or Wi-Fi session). */
fun interface AutomationActionSink {
    suspend fun dispatch(action: AutomationAction): Boolean
}

data class AutomationDispatchResult(val eventId: String, val status: AutomationEventStatus)

/**
 * Executes pending automation events. It never talks to hardware itself: it
 * delegates to [sink], which is bound to the current transport, and refuses to
 * run any hardware action while the link is not authorized.
 */
object AutomationCoordinator {
    suspend fun dispatch(
        pending: List<AutomationEvent>,
        authorized: Boolean,
        sink: AutomationActionSink?
    ): List<AutomationDispatchResult> = pending.map { event ->
        val status = when {
            event.action is AutomationAction.Notify -> AutomationEventStatus.NOTIFIED
            !authorized || sink == null -> AutomationEventStatus.BLOCKED
            else -> runCatching { sink.dispatch(event.action) }
                .fold(
                    onSuccess = { if (it) AutomationEventStatus.DISPATCHED else AutomationEventStatus.FAILED },
                    onFailure = { AutomationEventStatus.FAILED }
                )
        }
        AutomationDispatchResult(event.id, status)
    }
}
