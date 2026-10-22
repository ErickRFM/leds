package com.rgbcontroller.core.components.status

import com.rgbcontroller.core.components.model.ComponentTelemetryCodec

/**
 * UI truth source for DHT11/DHT22. An active GPIO lease only establishes
 * firmware configuration; two firmware-valid live measurements establish
 * sensor response. Never infer the sensor model from measurement names alone.
 */
object ModuleEvidence {
    fun dhtResponding(state: ComponentDashboardState, model: String): Boolean {
        if (model != "dht11" && model != "dht22") return false
        if (!state.connected || state.authorityError != null ||
            state.freshness != ComponentStatusFreshness.LIVE ||
            state.telemetryFreshness != ComponentStatusFreshness.LIVE) return false
        // Firmware sends the same temperature/humidity type names for both
        // models. Require EXACTLY one active DHT and a matching module id.
        val activeDht = state.authoritative.orEmpty()
            .filter { it.active && it.id in setOf("sensor.dht11", "sensor.dht22") }
        if (activeDht.size != 1 || activeDht.single().id != "sensor.$model")
            return false
        return listOf("temperature", "humidity").all { type ->
            ComponentTelemetryCodec.find(state.authoritativeTelemetry.orEmpty(), type)
                ?.let { it.valid && it.status == "fresh" && it.value.isFinite() } == true
        }
    }

    fun connectionAction(wifiAuthorized: Boolean, bleReady: Boolean): String =
        if (wifiAuthorized || bleReady) "Gestionar" else "Conectar"

    fun moduleStatus(
        id: String,
        firmwareActive: Boolean,
        currentlyVerified: Boolean,
        sensorResponding: Boolean
    ): String = when {
        !currentlyVerified -> "Último estado guardado · no en vivo"
        id == "led_rgb" && firmwareActive -> "PWM preparado · presencia LED no verificable"
        id in setOf("dht11", "dht22") && sensorResponding ->
            "Sensor respondiendo · datos en vivo"
        id in setOf("dht11", "dht22") && firmwareActive ->
            "GPIO activo · esperando lecturas válidas"
        firmwareActive -> "Controlador activo"
        else -> "Controlador inactivo"
    }
}
