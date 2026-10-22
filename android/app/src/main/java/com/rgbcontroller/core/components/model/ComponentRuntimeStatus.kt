package com.rgbcontroller.core.components.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Authoritative runtime status of one firmware component, as reported by
 * `GET /v2/components/status`. [pins] are the GPIO the firmware actually leased
 * and [active] is true only while the component holds its lease. This is the
 * firmware's view; the client never upgrades its own local configuration into
 * an authoritative state.
 */
data class ComponentRuntimeStatus(
    val id: String,
    val type: String,
    val interfaceType: String,
    val state: String,
    val active: Boolean,
    val pins: List<Int>,
    // GPIO readback is separate from physical validation. Only DHT firmware
    // reports these optional diagnostic fields (older firmware omits them).
    val diagnostic: String? = null,
    val failures: Int = 0
)

/**
 * Strict, bounded decoder for the firmware component-status contract. Rejects
 * payloads that do not assert `gpioAuthority == "firmware"` so a client can
 * never treat a locally-invented status as authoritative.
 */
object ComponentStatusCodec {
    const val MAX_COMPONENTS = 12
    const val GPIO_AUTHORITY = "firmware"

    fun decode(raw: String?): List<ComponentRuntimeStatus>? {
        if (raw.isNullOrBlank()) return null
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (root.optInt("version", 0) != 1) return null
        if (root.optString("gpioAuthority", "") != GPIO_AUTHORITY) return null
        val array = root.optJSONArray("components") ?: return null
        return parseComponents(array)
    }

    /** Bounded parser for the `components` array shared by transport and persistence. */
    fun parseComponents(array: JSONArray): List<ComponentRuntimeStatus> {
        val limit = minOf(array.length(), MAX_COMPONENTS)
        val out = ArrayList<ComponentRuntimeStatus>(limit)
        for (i in 0 until limit) {
            val entry = array.optJSONObject(i) ?: continue
            val id = entry.optString("id", "")
            if (id.isBlank()) continue
            val pinsArray = entry.optJSONArray("pins")
            val pins = ArrayList<Int>(pinsArray?.length() ?: 0)
            if (pinsArray != null) {
                for (p in 0 until pinsArray.length()) {
                    val pin = pinsArray.optInt(p, -1)
                    if (pin in 0..39) pins += pin
                }
            }
            out += ComponentRuntimeStatus(
                id = id,
                type = entry.optString("type", "unknown"),
                interfaceType = entry.optString("interface", "unknown"),
                state = entry.optString("state", "error"),
                active = entry.optBoolean("active", false),
                pins = pins,
                diagnostic = entry.optString("diagnostic", "").takeIf {
                    it in setOf("not_attempted", "ok", "line_low", "no_response",
                        "response_timeout", "bit_timeout", "bad_checksum",
                        "invalid_payload")
                },
                failures = entry.optInt("failures", 0).coerceIn(0, 65535)
            )
        }
        return out
    }

    /** Encodes a bounded component list (no credentials, no transport fields). */
    fun encodeComponents(components: List<ComponentRuntimeStatus>): JSONArray {
        val array = JSONArray()
        for (component in components.take(MAX_COMPONENTS)) {
            val entry = JSONObject()
            entry.put("id", component.id)
            entry.put("type", component.type)
            entry.put("interface", component.interfaceType)
            entry.put("state", component.state)
            entry.put("active", component.active)
            entry.put("pins", JSONArray(component.pins))
            component.diagnostic?.let {
                entry.put("diagnostic", it)
                entry.put("failures", component.failures)
            }
            array.put(entry)
        }
        return array
    }

    fun find(statuses: List<ComponentRuntimeStatus>, id: String): ComponentRuntimeStatus? =
        statuses.lastOrNull { it.id == id }

    /** Maps the firmware lifecycle string onto the UI component state. */
    fun stateFor(status: ComponentRuntimeStatus): ComponentState = when (status.state) {
        "active" -> ComponentState.ACTIVE
        "error" -> ComponentState.ERROR
        "unsupported" -> ComponentState.INCOMPATIBLE
        "disconnected" -> ComponentState.DISCONNECTED
        "configured", "detected" -> ComponentState.CONFIGURED
        "catalog" -> ComponentState.AVAILABLE
        else -> ComponentState.NOT_CONFIGURED
    }
}
