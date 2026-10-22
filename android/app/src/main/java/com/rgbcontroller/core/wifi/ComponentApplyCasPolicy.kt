package com.rgbcontroller.core.wifi

import org.json.JSONObject

/**
 * Compare-and-swap expectation for /v2/components/apply.
 * Inactive firmware modules report an EMPTY lease array, not their configured
 * defaults. Only an active module may report owned GPIO as its CAS predecessor.
 */
internal object ComponentApplyCasPolicy {
    fun expected(prior: JSONObject, count: Int): List<Int> {
        require(count in 1..3)
        val pins = prior.optJSONArray("pins") ?: error("El firmware no reporta GPIO.")
        val original = (0 until pins.length()).map { pins.optInt(it, -1) }
        val active = prior.optBoolean("active")
        if (active) {
            check(original.size == count && original.all { it in 0..39 }) {
                "El firmware no reporta las señales activas esperadas."
            }
            return original + List(maxOf(2, count) - count) { 0 }
        }
        check(original.isEmpty()) {
            "El firmware reportó pines sin una reserva activa; actualiza el estado antes de aplicar."
        }
        return List(maxOf(2, count)) { 0 }
    }
}
