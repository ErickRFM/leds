package com.rgbcontroller.core.export

import com.rgbcontroller.core.components.alerts.AlertComparison
import com.rgbcontroller.core.components.alerts.AlertRule
import com.rgbcontroller.core.components.alerts.AlertSeverity
import com.rgbcontroller.core.components.automation.AutomationAction
import com.rgbcontroller.core.components.automation.AutomationRule
import com.rgbcontroller.core.components.calibration.StoredSoilCalibration
import com.rgbcontroller.core.components.status.LocalDesiredComponent
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Versioned, portable project format used by F09 (local import/export).
 *
 * Pure Kotlin + org.json only: no Android, no secrets, no transport. The
 * canonical serialization is deterministic (sorted keys, no whitespace) and
 * carries a SHA-256 checksum over the canonical payload (everything except the
 * checksum field itself) so a tampered or truncated file is rejected, not
 * silently imported.
 */
data class ProjectExportEnvelope(
    val schema: Int,
    val app: String,
    val kind: String,
    val projectId: String,
    val name: String,
    val createdAtMs: Long,
    val boardProfile: String,
    val capabilities: List<String>,
    val components: List<LocalDesiredComponent>,
    val alerts: List<AlertRule>,
    val automations: List<AutomationRule>,
    val calibrations: List<StoredSoilCalibration>
) {
    private fun buildObject(): JSONObject {
        val json = JSONObject()
        json.put("schema", schema)
        json.put("app", app)
        json.put("kind", kind)
        json.put("projectId", projectId)
        json.put("name", name)
        json.put("createdAtMs", createdAtMs)
        json.put("boardProfile", boardProfile)
        json.put("capabilities", JSONArray(capabilities.sorted()))
        json.put("components", JSONArray().apply {
            components.sortedBy { it.id }.forEach { component ->
                put(JSONObject().apply {
                    put("id", component.id)
                    put("type", component.type)
                    put("pins", JSONArray(component.pins.sorted()))
                })
            }
        })
        json.put("alerts", JSONArray().apply {
            alerts.sortedBy { it.id }.forEach { rule ->
                put(JSONObject().apply {
                    put("id", rule.id)
                    put("componentId", rule.componentId)
                    put("measurementType", rule.measurementType)
                    put("comparison", rule.comparison.name)
                    put("threshold", rule.threshold.toDouble())
                    put("hysteresis", rule.hysteresis.toDouble())
                    put("minIntervalMs", rule.minIntervalMs)
                    put("severity", rule.severity.name)
                    put("enabled", rule.enabled)
                })
            }
        })
        json.put("automations", JSONArray().apply {
            automations.sortedBy { it.id }.forEach { rule ->
                put(JSONObject().apply {
                    put("id", rule.id)
                    put("componentId", rule.componentId)
                    put("measurementType", rule.measurementType)
                    put("comparison", rule.comparison.name)
                    put("threshold", rule.threshold.toDouble())
                    put("hysteresis", rule.hysteresis.toDouble())
                    put("minIntervalMs", rule.minIntervalMs)
                    put("enabled", rule.enabled)
                    val action = rule.action
                    put("actionType", actionTypeName(action))
                    when (action) {
                        is AutomationAction.Notify -> {
                            put("message", action.message)
                            put("severity", action.severity.name)
                        }
                        is AutomationAction.SetRgb -> {
                            put("red", action.red)
                            put("green", action.green)
                            put("blue", action.blue)
                            put("brightness", action.brightness)
                        }
                        is AutomationAction.UpdateOled -> put("oledText", action.text)
                    }
                })
            }
        })
        json.put("calibrations", JSONArray().apply {
            calibrations.sortedBy { it.deviceId }.forEach { calibration ->
                put(JSONObject().apply {
                    put("deviceId", calibration.deviceId)
                    put("componentId", calibration.componentId)
                    put("dryRaw", calibration.dryRaw)
                    put("wetRaw", calibration.wetRaw)
                })
            }
        })
        return json
    }

    fun canonicalPayload(): String = stableJson(buildObject())

    fun checksum(): String = sha256Hex(canonicalPayload())

    fun serialize(): String {
        val root = buildObject()
        root.put("checksum", sha256Hex(stableJson(root)))
        return stableJson(root)
    }

    companion object {
        const val APP = "compi"
        const val KIND = "project"
        const val SCHEMA_VERSION = 1
        const val MIN_SUPPORTED_SCHEMA = 1
        const val MAX_BYTES = 256 * 1024
        const val MAX_COMPONENTS = 64
        const val MAX_ALERTS = 64
        const val MAX_AUTOMATIONS = 64
        const val MAX_CALIBRATIONS = 16

        internal fun sha256Hex(text: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(text.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02X".format(it) }
        }

        private fun actionTypeName(action: AutomationAction): String = when (action) {
            is AutomationAction.Notify -> "notify"
            is AutomationAction.SetRgb -> "set_rgb"
            is AutomationAction.UpdateOled -> "update_oled"
        }
    }
}

/** Result of parsing an exported file: either a valid envelope or a safe reason. */
sealed interface ExportParseResult {
    data class Valid(val envelope: ProjectExportEnvelope) : ExportParseResult
    data class Rejected(val reason: String) : ExportParseResult
}

/**
 * Deterministic JSON serialization with recursively sorted keys. org.json does
 * not guarantee key order, so the checksum is computed over this stable form on
 * both the write and the read path.
 */
internal fun stableJson(value: Any?): String = when (value) {
    null -> "null"
    is JSONObject -> value.keys().asSequence().sorted()
        .joinToString(",", "{", "}") { "\"${escapeJson(it)}\":${stableJson(value.get(it))}" }
    is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") {
        stableJson(value.get(it))
    }
    is String -> "\"${escapeJson(value)}\""
    is Boolean -> value.toString()
    is Double -> if (value == value.toLong().toDouble() && value.isFinite()) {
        value.toLong().toString()
    } else {
        value.toString()
    }
    else -> value.toString()
}

private fun escapeJson(text: String): String {
    val builder = StringBuilder(text.length + 2)
    for (character in text) {
        when (character) {
            '"' -> builder.append("\\\"")
            '\\' -> builder.append("\\\\")
            '\n' -> builder.append("\\n")
            '\r' -> builder.append("\\r")
            '\t' -> builder.append("\\t")
            else -> if (character.code < 0x20) {
                builder.append("\\u%04x".format(character.code))
            } else {
                builder.append(character)
            }
        }
    }
    return builder.toString()
}

/**
 * Strict, fail-closed parser. Unknown schemas, oversized payloads, entry-count
 * overflows, malformed structure, missing checksum and checksum mismatches are
 * all rejected — never partially imported.
 */
object ProjectExportParser {

    fun parse(raw: String?): ExportParseResult {
        if (raw.isNullOrBlank()) {
            return ExportParseResult.Rejected("Archivo vacío o ilegible.")
        }
        if (raw.toByteArray(Charsets.UTF_8).size > ProjectExportEnvelope.MAX_BYTES) {
            return ExportParseResult.Rejected(
                "El archivo supera el tamaño máximo (${ProjectExportEnvelope.MAX_BYTES} bytes)."
            )
        }
        val root = try {
            JSONObject(raw)
        } catch (failure: Exception) {
            return ExportParseResult.Rejected("El archivo no es un proyecto JSON válido.")
        }
        if (!root.has("checksum") || !root.has("schema") || !root.has("kind") || !root.has("app")) {
            return ExportParseResult.Rejected("Faltan campos obligatorios del formato.")
        }
        val app = root.optString("app")
        if (app != ProjectExportEnvelope.APP) {
            return ExportParseResult.Rejected("Aplicación desconocida: $app.")
        }
        val kind = root.optString("kind")
        if (kind != ProjectExportEnvelope.KIND) {
            return ExportParseResult.Rejected("Tipo de proyecto desconocido: $kind.")
        }
        val schema = root.optInt("schema", -1)
        if (schema < ProjectExportEnvelope.MIN_SUPPORTED_SCHEMA || schema > ProjectExportEnvelope.SCHEMA_VERSION) {
            return ExportParseResult.Rejected(
                "Versión de esquema no soportada: $schema. Soportadas: " +
                    "${ProjectExportEnvelope.MIN_SUPPORTED_SCHEMA}..${ProjectExportEnvelope.SCHEMA_VERSION}."
            )
        }
        val storedChecksum = root.optString("checksum")
        if (storedChecksum.isBlank()) {
            return ExportParseResult.Rejected("Falta el checksum de integridad.")
        }
        // Recompute the checksum over the payload without the checksum field.
        val payload = JSONObject(root.toString())
        payload.remove("checksum")
        val recomputed = ProjectExportEnvelope.sha256Hex(stableJson(payload))
        if (!recomputed.equals(storedChecksum, ignoreCase = true)) {
            return ExportParseResult.Rejected("El checksum no coincide: el archivo fue alterado o está dañado.")
        }
        val projectId = root.optString("projectId")
        if (projectId.isBlank()) return ExportParseResult.Rejected("Falta el identificador del proyecto.")
        val name = root.optString("name")
        if (name.isBlank()) return ExportParseResult.Rejected("Falta el nombre del proyecto.")
        val createdAtMs = root.optLong("createdAtMs", 0L)
        val boardProfile = root.optString("boardProfile", "unknown")

        val capabilities = root.optJSONArray("capabilities").toStringList()
            .take(ProjectExportEnvelope.MAX_COMPONENTS)

        val components = parseComponents(root.optJSONArray("components"))
            ?: return ExportParseResult.Rejected("Componentes inválidos o demasiados.")
        val alerts = parseAlerts(root.optJSONArray("alerts"))
            ?: return ExportParseResult.Rejected("Alertas inválidas o demasiadas.")
        val automations = parseAutomations(root.optJSONArray("automations"))
            ?: return ExportParseResult.Rejected("Automatizaciones inválidas o demasiadas.")
        val calibrations = parseCalibrations(root.optJSONArray("calibrations"))
            ?: return ExportParseResult.Rejected("Calibraciones inválidas o demasiadas.")

        return ExportParseResult.Valid(
            ProjectExportEnvelope(
                schema = schema,
                app = app,
                kind = kind,
                projectId = projectId,
                name = name,
                createdAtMs = createdAtMs,
                boardProfile = boardProfile,
                capabilities = capabilities,
                components = components,
                alerts = alerts,
                automations = automations,
                calibrations = calibrations
            )
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            optString(index).takeIf { it.isNotBlank() }
        }
    }

    private fun parseComponents(array: JSONArray?): List<LocalDesiredComponent>? {
        if (array == null) return emptyList()
        if (array.length() > ProjectExportEnvelope.MAX_COMPONENTS) return null
        val parsed = mutableListOf<LocalDesiredComponent>()
        val seen = mutableSetOf<String>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: return null
            val id = item.optString("id")
            if (id.isBlank() || !seen.add(id)) return null
            val type = item.optString("type")
            if (type.isBlank()) return null
            val pinsArray = item.optJSONArray("pins")
            val pins = (0 until (pinsArray?.length() ?: 0)).map {
                pinsArray!!.optInt(it, -1)
            }
            if (pins.any { it !in 0..39 }) return null
            parsed += LocalDesiredComponent(id, type, pins)
        }
        return parsed
    }

    private fun parseAlerts(array: JSONArray?): List<AlertRule>? {
        if (array == null) return emptyList()
        if (array.length() > ProjectExportEnvelope.MAX_ALERTS) return null
        val parsed = mutableListOf<AlertRule>()
        val seen = mutableSetOf<String>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: return null
            val id = item.optString("id")
            if (id.isBlank() || !seen.add(id)) return null
            val comparison = parseComparison(item.optString("comparison")) ?: return null
            val severity = parseSeverity(item.optString("severity")) ?: return null
            val threshold = item.optDouble("threshold", Double.NaN)
            if (!threshold.isFinite() || threshold < 0.0) return null
            val hysteresis = item.optDouble("hysteresis", 0.0)
            if (!hysteresis.isFinite() || hysteresis < 0.0) return null
            val minIntervalMs = item.optLong("minIntervalMs", 0L)
            if (minIntervalMs < 0L) return null
            parsed += AlertRule(
                id = id,
                componentId = item.optString("componentId"),
                measurementType = item.optString("measurementType"),
                comparison = comparison,
                threshold = threshold.toFloat(),
                hysteresis = hysteresis.toFloat(),
                minIntervalMs = minIntervalMs,
                severity = severity,
                enabled = item.optBoolean("enabled", true)
            )
        }
        return parsed
    }

    private fun parseAutomations(array: JSONArray?): List<AutomationRule>? {
        if (array == null) return emptyList()
        if (array.length() > ProjectExportEnvelope.MAX_AUTOMATIONS) return null
        val parsed = mutableListOf<AutomationRule>()
        val seen = mutableSetOf<String>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: return null
            val id = item.optString("id")
            if (id.isBlank() || !seen.add(id)) return null
            val comparison = parseComparison(item.optString("comparison")) ?: return null
            val threshold = item.optDouble("threshold", Double.NaN)
            if (!threshold.isFinite() || threshold < 0.0) return null
            val hysteresis = item.optDouble("hysteresis", 0.0)
            if (!hysteresis.isFinite() || hysteresis < 0.0) return null
            val minIntervalMs = item.optLong("minIntervalMs", 0L)
            if (minIntervalMs < 0L) return null
            val action = when (item.optString("actionType")) {
                "notify" -> AutomationAction.Notify(
                    message = item.optString("message"),
                    severity = parseSeverity(item.optString("severity")) ?: AlertSeverity.INFO
                )
                "set_rgb" -> AutomationAction.SetRgb(
                    red = item.optInt("red", -1),
                    green = item.optInt("green", -1),
                    blue = item.optInt("blue", -1),
                    brightness = item.optInt("brightness", -1)
                )
                "update_oled" -> AutomationAction.UpdateOled(text = item.optString("oledText"))
                else -> return null
            }
            if (action is AutomationAction.SetRgb) {
                if (action.red !in 0..255 || action.green !in 0..255 ||
                    action.blue !in 0..255 || action.brightness !in 0..100
                ) return null
            }
            if (action is AutomationAction.Notify && action.message.isBlank()) return null
            if (action is AutomationAction.UpdateOled && action.text.isBlank()) return null
            parsed += AutomationRule(
                id = id,
                componentId = item.optString("componentId"),
                measurementType = item.optString("measurementType"),
                comparison = comparison,
                threshold = threshold.toFloat(),
                hysteresis = hysteresis.toFloat(),
                minIntervalMs = minIntervalMs,
                action = action,
                enabled = item.optBoolean("enabled", false)
            )
        }
        return parsed
    }

    private fun parseCalibrations(array: JSONArray?): List<StoredSoilCalibration>? {
        if (array == null) return emptyList()
        if (array.length() > ProjectExportEnvelope.MAX_CALIBRATIONS) return null
        val parsed = mutableListOf<StoredSoilCalibration>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: return null
            val deviceId = item.optString("deviceId")
            if (deviceId.isBlank()) return null
            val dryRaw = item.optInt("dryRaw", -1)
            val wetRaw = item.optInt("wetRaw", -1)
            val calibration = runCatching {
                StoredSoilCalibration(
                    deviceId = deviceId,
                    componentId = item.optString("componentId", "soil_moisture"),
                    dryRaw = dryRaw,
                    wetRaw = wetRaw
                )
            }.getOrNull() ?: return null
            parsed += calibration
        }
        return parsed
    }

    private fun parseComparison(value: String): AlertComparison? =
        AlertComparison.values().firstOrNull { it.name == value }

    private fun parseSeverity(value: String): AlertSeverity? =
        AlertSeverity.values().firstOrNull { it.name == value }
}

/** Builds a canonical envelope from the live local configuration (no secrets). */
object ProjectExportBuilder {
    fun build(
        projectId: String,
        name: String,
        boardProfile: String,
        capabilities: List<String>,
        components: List<LocalDesiredComponent>,
        alerts: List<AlertRule>,
        automations: List<AutomationRule>,
        calibrations: List<StoredSoilCalibration>,
        nowMs: Long
    ): ProjectExportEnvelope = ProjectExportEnvelope(
        schema = ProjectExportEnvelope.SCHEMA_VERSION,
        app = ProjectExportEnvelope.APP,
        kind = ProjectExportEnvelope.KIND,
        projectId = projectId,
        name = name,
        createdAtMs = nowMs,
        boardProfile = boardProfile,
        capabilities = capabilities,
        components = components,
        alerts = alerts,
        automations = automations,
        calibrations = calibrations
    )
}
