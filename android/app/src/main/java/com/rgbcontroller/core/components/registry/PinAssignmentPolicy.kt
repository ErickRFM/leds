package com.rgbcontroller.core.components.registry

import com.rgbcontroller.core.components.model.ComponentRuntimeStatus
import com.rgbcontroller.core.components.status.LocalDesiredComponent

/** V17: the signals actually needed for a component, NOT all compatible GPIOs. */
data class ModuleSignal(
    val label: String,
    val allowedPins: Set<Int>,
    val suggestedPin: Int
)

data class ModuleWiringProfile(
    val componentId: String,
    val signals: List<ModuleSignal>
) {
    val defaults: List<Int> get() = signals.map { it.suggestedPin }
}

/**
 * Conservative pinouts for the legacy ESP32-D0WD profile.
 * GPIO 6-11 (flash), strapping pins and input-only pins are never offered
 * as general-purpose outputs. These are suggestions, NOT firmware authority.
 */
object PinAssignmentPolicy {
    // Matches the ESP32 PinManager output policy. GPIO 25/26/27 are reserved
    // for led.rgb by firmware at boot, even when its current lease is inactive.
    val rgbReserved = setOf(25, 26, 27)
    val generalOutputs = setOf(16, 17, 18, 19, 21, 22, 23, 32, 33)
    val pwmOutputs = generalOutputs + rgbReserved
    val adc1Inputs = setOf(32, 33, 34, 35, 36, 39)
    val digitalInputs = generalOutputs + setOf(34, 35, 36, 39)

    private fun signal(name: String, pins: Set<Int>, default: Int) =
        ModuleSignal(name, pins, default)

    val profiles: List<ModuleWiringProfile> = listOf(
        ModuleWiringProfile("led_rgb", listOf(
            signal("Rojo · R", pwmOutputs, 27),
            signal("Verde · G", pwmOutputs, 25),
            signal("Azul · B", pwmOutputs, 26)
        )),
        ModuleWiringProfile("dht11", listOf(signal("Datos · DATA", generalOutputs, 16))),
        ModuleWiringProfile("dht22", listOf(signal("Datos · DATA", generalOutputs, 32))),
        ModuleWiringProfile("soil_moisture", listOf(signal("Salida analógica · ADC1", adc1Inputs, 34))),
        ModuleWiringProfile("ssd1306_oled", listOf(
            signal("Datos I²C · SDA", generalOutputs, 21),
            signal("Reloj I²C · SCL", generalOutputs, 22)
        )),
        ModuleWiringProfile("analog_sensor", listOf(signal("ADC1", adc1Inputs, 35))),
        ModuleWiringProfile("digital_sensor", listOf(signal("Entrada digital", digitalInputs, 18))),
        ModuleWiringProfile("actuator_relay", listOf(signal("Control de relé", generalOutputs, 19)))
    )

    fun profile(id: String): ModuleWiringProfile? = profiles.firstOrNull { it.componentId == id }

    /**
     * Validate a LOCAL draft against known local reservations and ACTIVE,
     * firmware-reported leases. Never implies the firmware accepted the draft.
     */
    fun validate(
        id: String,
        pins: List<Int>,
        otherLocal: List<LocalDesiredComponent> = emptyList(),
        reported: List<ComponentRuntimeStatus> = emptyList()
    ): List<String> {
        val profile = profile(id) ?: return listOf("Componente sin perfil de pines compatible.")
        val problems = mutableListOf<String>()
        if (pins.size != profile.signals.size) {
            problems += "Se necesitan ${profile.signals.size} señales; se asignaron ${pins.size}."
            return problems
        }
        if (pins.distinct().size != pins.size) problems += "Un GPIO no puede asignarse a dos señales."
        for ((index, pin) in pins.withIndex()) {
            if (pin !in profile.signals[index].allowedPins) {
                problems += "${profile.signals[index].label}: GPIO ${pin} no está permitido en este perfil."
            }
        }
        val family = setOf("dht11", "dht22")
        // A model change replaces the current DHT lease atomically in firmware;
        // the sibling DHT model must not block intentional GPIO transfers.
        val used = otherLocal.filter {
            it.id != id && !(id in family && it.id in family)
        }.flatMap { other ->
            other.pins.map { it to other.id }
        }
        for ((pin, owner) in used) {
            if (pin in pins) problems += "GPIO ${pin} ya reservado localmente por ${owner}."
        }
        for (component in reported) {
            val otherId = ComponentCatalog.catalogIdForFirmwareId(component.id)
            if (otherId != id && !(id in family && otherId in family) &&
                component.active && component.pins.any { it in pins }
            ) {
                problems += "El firmware usa GPIO ${component.pins.filter { it in pins }.joinToString()} en ${component.id}."
            }
        }
        return problems.distinct()
    }
}
