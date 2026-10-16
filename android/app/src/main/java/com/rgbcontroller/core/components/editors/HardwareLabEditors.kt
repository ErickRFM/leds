package com.rgbcontroller.core.components.editors

import com.rgbcontroller.core.components.alerts.AlertComparison
import com.rgbcontroller.core.components.alerts.AlertRule
import com.rgbcontroller.core.components.alerts.AlertSeverity
import com.rgbcontroller.core.components.automation.AutomationAction
import com.rgbcontroller.core.components.automation.AutomationRule
import com.rgbcontroller.core.components.model.HardwareComponentDescriptor
import com.rgbcontroller.core.components.registry.ComponentCatalog
import com.rgbcontroller.core.components.status.LocalDesiredComponent
import com.rgbcontroller.core.sensors.SoilCalibration

/** One problem found while editing. [field] lets the UI point at the input. */
data class EditorIssue(val field: String, val message: String)

/**
 * Result of validating a draft. [value] is only non-null when the draft is
 * fully valid, so an invalid form can never be turned into a persisted rule.
 */
data class EditorResult<T>(val value: T?, val issues: List<EditorIssue>) {
    val isValid: Boolean get() = issues.isEmpty() && value != null

    companion object {
        fun <T> ok(value: T): EditorResult<T> = EditorResult(value, emptyList())
        fun <T> fail(vararg issues: EditorIssue): EditorResult<T> = EditorResult(null, issues.toList())
    }
}

/** Parsing helpers shared by the editors; never throws on user input. */
internal object DraftNumbers {
    fun positive(text: String): Float? =
        text.trim().replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() }

    fun nonNegative(text: String, field: String, issues: MutableList<EditorIssue>): Float {
        val value = positive(text)
        if (value == null || value < 0f) {
            issues += EditorIssue(field, "Introduce un número ≥ 0.")
            return 0f
        }
        return value
    }

    fun intInRange(text: String, range: IntRange, field: String, issues: MutableList<EditorIssue>): Int {
        val value = text.trim().toIntOrNull()
        if (value == null || value !in range) {
            issues += EditorIssue(field, "Introduce un entero entre ${range.first} y ${range.last}.")
            return range.first
        }
        return value
    }
}

/** Components with a chartable telemetry series the editors may target. */
object AlertRuleEditor {
    val components: List<String> = listOf("dht22", "soil_moisture")

    fun measurementTypesFor(componentId: String): List<String> = when (componentId) {
        "dht22" -> listOf("temperature", "humidity")
        "soil_moisture" -> listOf("soil_moisture")
        else -> emptyList()
    }
}

/** Editable form for one [AlertRule]. */
data class AlertRuleDraft(
    val id: String,
    val componentId: String,
    val measurementType: String,
    val comparison: AlertComparison,
    val thresholdText: String,
    val hysteresisText: String,
    val minIntervalSecondsText: String,
    val severity: AlertSeverity,
    val enabled: Boolean
) {
    fun validate(): EditorResult<AlertRule> {
        val issues = mutableListOf<EditorIssue>()
        if (id.isBlank()) issues += EditorIssue("id", "Falta el identificador.")
        if (componentId !in AlertRuleEditor.components) {
            issues += EditorIssue("component", "Componente desconocido.")
        }
        if (measurementType !in AlertRuleEditor.measurementTypesFor(componentId)) {
            issues += EditorIssue("measurement", "Serie no válida para el componente.")
        }
        val threshold = DraftNumbers.positive(thresholdText)
        if (threshold == null) issues += EditorIssue("threshold", "Umbral inválido.")
        val hysteresis = DraftNumbers.nonNegative(hysteresisText, "hysteresis", issues)
        val interval = DraftNumbers
            .intInRange(minIntervalSecondsText, 0..86_400, "interval", issues).toLong() * 1000L
        if (issues.isNotEmpty() || threshold == null) return EditorResult(null, issues)
        return EditorResult.ok(
            AlertRule(
                id = id, componentId = componentId, measurementType = measurementType,
                comparison = comparison, threshold = threshold, hysteresis = hysteresis,
                minIntervalMs = interval, severity = severity, enabled = enabled
            )
        )
    }

    companion object {
        fun from(rule: AlertRule): AlertRuleDraft = AlertRuleDraft(
            id = rule.id, componentId = rule.componentId, measurementType = rule.measurementType,
            comparison = rule.comparison, thresholdText = rule.threshold.toString(),
            hysteresisText = rule.hysteresis.toString(),
            minIntervalSecondsText = (rule.minIntervalMs / 1000L).toString(),
            severity = rule.severity, enabled = rule.enabled
        )

        fun blank(componentId: String = "dht22"): AlertRuleDraft = AlertRuleDraft(
            id = "alert_${System.currentTimeMillis()}", componentId = componentId,
            measurementType = AlertRuleEditor.measurementTypesFor(componentId).first(),
            comparison = AlertComparison.ABOVE, thresholdText = "", hysteresisText = "0",
            minIntervalSecondsText = "60", severity = AlertSeverity.WARNING, enabled = true
        )
    }
}

enum class AutomationActionType { NOTIFY, SET_RGB, UPDATE_OLED }

/** Editable form for one [AutomationRule]. */
data class AutomationRuleDraft(
    val id: String,
    val componentId: String,
    val measurementType: String,
    val comparison: AlertComparison,
    val thresholdText: String,
    val hysteresisText: String,
    val minIntervalSecondsText: String,
    val actionType: AutomationActionType,
    val messageText: String,
    val severity: AlertSeverity,
    val redText: String,
    val greenText: String,
    val blueText: String,
    val brightnessText: String,
    val oledText: String,
    val enabled: Boolean
) {
    fun validate(): EditorResult<AutomationRule> {
        val issues = mutableListOf<EditorIssue>()
        if (id.isBlank()) issues += EditorIssue("id", "Falta el identificador.")
        if (componentId !in AlertRuleEditor.components) {
            issues += EditorIssue("component", "Componente desconocido.")
        }
        if (measurementType !in AlertRuleEditor.measurementTypesFor(componentId)) {
            issues += EditorIssue("measurement", "Serie no válida para el componente.")
        }
        val threshold = DraftNumbers.positive(thresholdText)
        if (threshold == null) issues += EditorIssue("threshold", "Umbral inválido.")
        val hysteresis = DraftNumbers.nonNegative(hysteresisText, "hysteresis", issues)
        val interval = DraftNumbers
            .intInRange(minIntervalSecondsText, 0..86_400, "interval", issues).toLong() * 1000L
        val action = when (actionType) {
            AutomationActionType.NOTIFY -> {
                if (messageText.isBlank()) issues += EditorIssue("message", "Escribe el aviso.")
                AutomationAction.Notify(messageText.trim(), severity)
            }
            AutomationActionType.SET_RGB -> AutomationAction.SetRgb(
                red = DraftNumbers.intInRange(redText, 0..255, "red", issues),
                green = DraftNumbers.intInRange(greenText, 0..255, "green", issues),
                blue = DraftNumbers.intInRange(blueText, 0..255, "blue", issues),
                brightness = DraftNumbers.intInRange(brightnessText, 0..100, "brightness", issues)
            )
            AutomationActionType.UPDATE_OLED -> {
                if (oledText.isBlank()) issues += EditorIssue("oled", "Escribe el texto del OLED.")
                AutomationAction.UpdateOled(oledText.trim())
            }
        }
        if (issues.isNotEmpty() || threshold == null) return EditorResult(null, issues)
        return EditorResult.ok(
            AutomationRule(
                id = id, componentId = componentId, measurementType = measurementType,
                comparison = comparison, threshold = threshold, hysteresis = hysteresis,
                minIntervalMs = interval, action = action, enabled = enabled
            )
        )
    }

    companion object {
        fun from(rule: AutomationRule): AutomationRuleDraft {
            val action = rule.action
            return AutomationRuleDraft(
                id = rule.id, componentId = rule.componentId, measurementType = rule.measurementType,
                comparison = rule.comparison, thresholdText = rule.threshold.toString(),
                hysteresisText = rule.hysteresis.toString(),
                minIntervalSecondsText = (rule.minIntervalMs / 1000L).toString(),
                actionType = when (action) {
                    is AutomationAction.Notify -> AutomationActionType.NOTIFY
                    is AutomationAction.SetRgb -> AutomationActionType.SET_RGB
                    is AutomationAction.UpdateOled -> AutomationActionType.UPDATE_OLED
                },
                messageText = (action as? AutomationAction.Notify)?.message ?: "",
                severity = (action as? AutomationAction.Notify)?.severity ?: AlertSeverity.INFO,
                redText = (action as? AutomationAction.SetRgb)?.red?.toString() ?: "255",
                greenText = (action as? AutomationAction.SetRgb)?.green?.toString() ?: "0",
                blueText = (action as? AutomationAction.SetRgb)?.blue?.toString() ?: "0",
                brightnessText = (action as? AutomationAction.SetRgb)?.brightness?.toString() ?: "100",
                oledText = (action as? AutomationAction.UpdateOled)?.text ?: "",
                enabled = rule.enabled
            )
        }

        fun blank(componentId: String = "dht22"): AutomationRuleDraft = AutomationRuleDraft(
            id = "auto_${System.currentTimeMillis()}", componentId = componentId,
            measurementType = AlertRuleEditor.measurementTypesFor(componentId).first(),
            comparison = AlertComparison.ABOVE, thresholdText = "", hysteresisText = "1",
            minIntervalSecondsText = "60", actionType = AutomationActionType.NOTIFY,
            messageText = "", severity = AlertSeverity.WARNING,
            redText = "255", greenText = "0", blueText = "0", brightnessText = "100",
            oledText = "", enabled = false
        )
    }
}

/** Editable form for the two soil-moisture references. */
data class SoilCalibrationDraft(
    val dryRawText: String,
    val wetRawText: String
) {
    fun validate(): EditorResult<SoilCalibration> {
        val issues = mutableListOf<EditorIssue>()
        val dry = DraftNumbers.intInRange(dryRawText, 0..4095, "dry", issues)
        val wet = DraftNumbers.intInRange(wetRawText, 0..4095, "wet", issues)
        if (issues.isNotEmpty()) return EditorResult(null, issues)
        val calibration = runCatching { SoilCalibration(dry, wet) }.getOrNull()
            ?: return EditorResult.fail(
                EditorIssue("dry", "Las referencias seco/húmedo deben diferir al menos 100.")
            )
        return EditorResult.ok(calibration)
    }

    companion object {
        fun blank() = SoilCalibrationDraft("", "")
    }
}

/** Editable form for one reusable local component ([LocalDesiredComponent]). */
data class ComponentConfigDraft(
    val id: String,
    val pinsText: String
) {
    fun validate(
        catalog: List<HardwareComponentDescriptor> = ComponentCatalog.components
    ): EditorResult<LocalDesiredComponent> {
        val issues = mutableListOf<EditorIssue>()
        val descriptor = catalog.find { it.id == id.trim() }
        if (descriptor == null) issues += EditorIssue("id", "El componente no está en el catálogo.")
        val pins = parsePins(pinsText, issues)
        if (descriptor != null && descriptor.requiredPins.isNotEmpty() &&
            pins.isNotEmpty() && !pins.containsAll(descriptor.requiredPins)
        ) {
            issues += EditorIssue(
                "pins",
                "Faltan GPIO requeridos: ${descriptor.requiredPins.sorted().joinToString(", ")}."
            )
        }
        if (issues.isNotEmpty() || descriptor == null) return EditorResult(null, issues)
        return EditorResult.ok(
            LocalDesiredComponent(
                id = descriptor.id,
                type = descriptor.type.name.lowercase(),
                pins = pins
            )
        )
    }

    private fun parsePins(text: String, issues: MutableList<EditorIssue>): List<Int> {
        val tokens = text.split(',', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() }
        val pins = tokens.mapNotNull { it.toIntOrNull() }
        if (pins.size != tokens.size) {
            issues += EditorIssue("pins", "Los GPIO deben ser números separados por comas.")
        }
        if (pins.any { it !in 0..39 }) {
            issues += EditorIssue("pins", "GPIO fuera de rango (0–39).")
        }
        if (pins.size != pins.toSet().size) {
            issues += EditorIssue("pins", "GPIO repetidos.")
        }
        return pins
    }

    companion object {
        fun from(component: LocalDesiredComponent) =
            ComponentConfigDraft(component.id, component.pins.joinToString(", "))

        fun blank() = ComponentConfigDraft("", "")
    }
}

/**
 * Read-only validation of an already-loaded configuration. Surfaces problems in
 * persisted data instead of silently trusting it.
 */
object LabConfigValidation {
    fun validateDesired(components: List<LocalDesiredComponent>): List<EditorIssue> {
        val issues = mutableListOf<EditorIssue>()
        val duplicated = components.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        for (id in duplicated) issues += EditorIssue(id, "Componente duplicado en la configuración local.")
        for (component in components) {
            if (ComponentCatalog.get(component.id) == null) {
                issues += EditorIssue(component.id, "El componente no está en el catálogo.")
            }
        }
        return issues
    }
}
