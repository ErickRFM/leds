package com.rgbcontroller.core.modules

import org.json.JSONObject

/** Read-only capability manifest. A declaration is not proof of a wired sensor or ownership. */
enum class ModuleKind { RGB, ADC_INPUT }

data class CompiModule(
    val id: String,
    val kind: ModuleKind,
    val title: String,
    val pins: List<Int>
)

object ModuleManifest {
    private const val MAX_BYTES = 4096
    private const val MAX_MODULES = 8

    /** Fail closed on malformed/incompatible manifests; unknown future modules are not rendered. */
    fun parse(json: String): List<CompiModule> {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Manifiesto demasiado grande" }
        val root = JSONObject(json)
        require(root.optInt("schemaVersion", -1) == 1) { "Versión de módulos incompatible" }
        val modules = root.optJSONArray("modules") ?: error("No hay catálogo de módulos")
        require(modules.length() <= MAX_MODULES) { "Demasiados módulos" }
        val ids = mutableSetOf<String>()
        val result = mutableListOf<CompiModule>()
        for (index in 0 until modules.length()) {
            val item = modules.optJSONObject(index) ?: error("Módulo inválido")
            val id = item.optString("id")
            require(id.length in 1..48 && id.matches(Regex("[a-z0-9._-]+")) && ids.add(id)) {
                "ID de módulo inválido o repetido"
            }
            val pinsJson = item.optJSONArray("pins") ?: error("Pines ausentes")
            require(pinsJson.length() in 1..8) { "Lista de pines inválida" }
            val pins = (0 until pinsJson.length()).map { pinIndex ->
                val pin = pinsJson.optInt(pinIndex, -1)
                require(pin in 0..39) { "GPIO inválido" }
                pin
            }
            require(pins.distinct().size == pins.size) { "Pines repetidos" }
            val kind = item.optString("kind")
            val uiKind = item.optString("uiKind")
            val title = item.optString("title")
            require(title.length in 1..48) { "Título inválido" }

            // Explicit allowlist; no dynamic code, HTML, or UI class names from the device.
            val resolved = when {
                id == "led.rgb.main" && kind == "actuator.rgb" &&
                    uiKind == "rgb-control" && pins == listOf(27, 25, 26) ->
                    ModuleKind.RGB
                id == "input.adc1.34" && kind == "sensor.adc_raw" &&
                    uiKind == "adc-gauge" && pins == listOf(34) ->
                    ModuleKind.ADC_INPUT
                else -> null
            }
            if (resolved != null) result.add(CompiModule(id, resolved, title, pins))
        }
        return result
    }
}
