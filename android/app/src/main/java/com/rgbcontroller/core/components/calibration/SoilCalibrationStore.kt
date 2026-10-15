package com.rgbcontroller.core.components.calibration

import com.rgbcontroller.core.sensors.SoilCalibration
import org.json.JSONObject

/**
 * Persisted soil calibration. It belongs to the exact authenticated device and
 * module it was captured on, plus the calibration format version. A record that
 * does not match the current device/module/version must be treated as
 * incompatible: it is ignored instead of being silently reused, so a raw ADC is
 * never turned into a moisture percentage for the wrong probe.
 *
 * Never stores credentials.
 */
data class StoredSoilCalibration(
    val deviceId: String,
    val componentId: String = COMPONENT_ID,
    val version: Int = VERSION,
    val dryRaw: Int,
    val wetRaw: Int
) {
    init {
        // Reuse the ADC range/ordering rules so an invalid pair can never be
        // persisted or decoded.
        SoilCalibration(dryRaw, wetRaw)
    }

    fun toCalibration(): SoilCalibration = SoilCalibration(dryRaw, wetRaw)

    /** True only for the same device, the same module and a supported version. */
    fun appliesTo(deviceId: String, componentId: String = COMPONENT_ID): Boolean =
        this.deviceId == deviceId && this.componentId == componentId && version == VERSION

    companion object {
        const val VERSION = 1
        const val COMPONENT_ID = "soil_moisture"
    }
}

/** Versioned codec for the persisted soil calibration. */
object SoilCalibrationCodec {
    fun encode(stored: StoredSoilCalibration): String = JSONObject().apply {
        put("version", stored.version)
        put("deviceId", stored.deviceId)
        put("componentId", stored.componentId)
        put("dryRaw", stored.dryRaw)
        put("wetRaw", stored.wetRaw)
    }.toString()

    fun decode(raw: String?): StoredSoilCalibration? {
        if (raw.isNullOrBlank()) return null
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (root.optInt("version", -1) != StoredSoilCalibration.VERSION) return null
        val deviceId = root.optString("deviceId", "")
        if (deviceId.isBlank()) return null
        val componentId = root.optString("componentId", StoredSoilCalibration.COMPONENT_ID)
        val dryRaw = root.optInt("dryRaw", -1)
        val wetRaw = root.optInt("wetRaw", -1)
        val stored = runCatching {
            StoredSoilCalibration(deviceId, componentId, dryRaw = dryRaw, wetRaw = wetRaw)
        }.getOrNull() ?: return null
        return stored
    }
}
