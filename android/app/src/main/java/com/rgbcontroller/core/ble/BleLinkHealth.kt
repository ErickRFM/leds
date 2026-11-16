package com.rgbcontroller.core.ble

/**
 * Link metrics are radio facts, never permissions. An excellent RSSI does not
 * authorize a command and a missing measurement is shown as missing, not guessed.
 */
enum class BleLinkQuality { UNKNOWN, STRONG, FAIR, WEAK, CRITICAL }

data class BleLinkHealth(
    val connected: Boolean = false,
    val mtu: Int? = null,
    val rssiDb: Int? = null,
    val measuredAtMs: Long = 0L,
    val staleNotice: String? = null
) {
    /** ATT removes three header bytes from every notification payload. */
    val payloadBytes: Int? get() = mtu?.minus(3)?.takeIf { it > 0 }

    val quality: BleLinkQuality
        get() = when {
            !connected || rssiDb == null -> BleLinkQuality.UNKNOWN
            rssiDb >= -60 -> BleLinkQuality.STRONG
            rssiDb >= -74 -> BleLinkQuality.FAIR
            rssiDb >= -89 -> BleLinkQuality.WEAK
            else -> BleLinkQuality.CRITICAL
        }

    fun ageSeconds(nowMs: Long): Int? {
        if (!connected || measuredAtMs <= 0L || nowMs < measuredAtMs) return null
        return ((nowMs - measuredAtMs) / 1000L).toInt()
    }

    fun ageText(nowMs: Long): String? = when (val age = ageSeconds(nowMs)) {
        null -> null
        0 -> "recién medida"
        1 -> "hace 1 s"
        else -> "hace $age s"
    }

    fun rssiText(): String? = rssiDb?.let { "$it dBm" }

    fun payloadText(): String? = payloadBytes?.let { "payload $it B" }

    fun qualityText(): String = when (quality) {
        BleLinkQuality.UNKNOWN -> "Enlace sin medir"
        BleLinkQuality.STRONG -> "Enlace fuerte"
        BleLinkQuality.FAIR -> "Enlace aceptable"
        BleLinkQuality.WEAK -> "Enlace débil"
        BleLinkQuality.CRITICAL -> "Enlace al límite"
    }

    /** Everything the header claims, in one line, with the age of the reading. */
    fun summary(nowMs: Long): String = buildList {
        add(qualityText())
        rssiText()?.let { add(it) }
        ageText(nowMs)?.let { add(it) }
        payloadText()?.let { add(it) }
    }.joinToString(" · ")
}
