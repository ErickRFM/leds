package com.rgbcontroller.core.components.status

import com.rgbcontroller.core.components.model.ComponentStatusCodec
import org.json.JSONArray
import org.json.JSONObject

/**
 * Versioned, bounded codecs for locally persisted component state. Persisted
 * documents never contain credentials, transport keys or BLE UUIDs: only the
 * device identifier and the already-public component status.
 */
object LastKnownStatusCodec {
    const val VERSION = 1

    fun encode(status: LastKnownStatus): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("deviceId", status.deviceId)
        root.put("confirmedAtMs", status.confirmedAtMs)
        root.put("components", ComponentStatusCodec.encodeComponents(status.components))
        return root.toString()
    }

    fun decode(raw: String?): LastKnownStatus? {
        if (raw.isNullOrBlank()) return null
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (root.optInt("version", 0) != VERSION) return null
        val deviceId = root.optString("deviceId", "")
        if (deviceId.isBlank()) return null
        val array = root.optJSONArray("components") ?: return null
        return LastKnownStatus(
            deviceId = deviceId,
            components = ComponentStatusCodec.parseComponents(array),
            confirmedAtMs = root.optLong("confirmedAtMs", 0L).coerceAtLeast(0L)
        )
    }
}

object LocalDesiredConfigCodec {
    const val VERSION = 1

    fun encode(config: LocalDesiredConfig): String {
        val root = JSONObject()
        root.put("version", VERSION)
        val array = JSONArray()
        for (component in config.components) {
            val entry = JSONObject()
            entry.put("id", component.id)
            entry.put("type", component.type)
            entry.put("pins", JSONArray(component.pins))
            array.put(entry)
        }
        root.put("components", array)
        return root.toString()
    }

    fun decode(raw: String?): LocalDesiredConfig {
        if (raw.isNullOrBlank()) return LocalDesiredConfig.EMPTY
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return LocalDesiredConfig.EMPTY
        if (root.optInt("version", 0) != VERSION) return LocalDesiredConfig.EMPTY
        val array = root.optJSONArray("components") ?: return LocalDesiredConfig.EMPTY
        val components = ArrayList<LocalDesiredComponent>(array.length())
        for (i in 0 until array.length()) {
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
            components += LocalDesiredComponent(
                id = id,
                type = entry.optString("type", "unknown"),
                pins = pins
            )
        }
        return LocalDesiredConfig(components)
    }
}
