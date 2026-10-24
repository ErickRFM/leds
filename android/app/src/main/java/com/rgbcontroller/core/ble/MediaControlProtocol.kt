package com.rgbcontroller.core.ble

/** State reported by the controller, never inferred from a button or UART write. */
data class AudioHardwareState(
    val modulePresent: Boolean, val sdPresent: Int, val tracks: Int,
    val playback: String, val track: Int, val volume: Int,
    val voiceEnabled: Boolean, val fresh: Boolean
) {
    // Playback requires independently confirmed module and SD state, not unrelated
    // track/count/volume polling, but rejects known empty cards (tracks == 0).
    val canPlay: Boolean get() = modulePresent && sdPresent == 1 && tracks != 0
    val playing: Boolean get() = modulePresent && playback == "PLAYING"
}

data class OledHardwareState(
    val ready: Boolean, val scene: String, val contrast: Int,
    val inverted: Boolean, val paused: Boolean
)

object MediaControlProtocol {
    val faces = setOf("happy", "sleepy", "angry", "surprised", "pumpkin", "cat", "dance")
    data class Reply(val id: String, val result: String,
                     val audio: AudioHardwareState? = null, val oled: OledHardwareState? = null)

    private fun validId(id: String) = id.length in 1..10 &&
        id.all { it in '0'..'9' || it in 'a'..'z' || it in 'A'..'Z' || it == '_' || it == '-' }
    private fun number(value: String?, range: IntRange): Boolean = value != null &&
        value.isNotEmpty() && value.all { it in '0'..'9' } && value.toIntOrNull() in range

    fun request(module: String, id: String, verb: String, value: String? = null): String? {
        if (!validId(id)) return null
        val valid = when (module) {
            "AUD" -> when (verb) {
                "GET", "PAUSE", "RESUME", "STOP" -> value == null
                "PLAY" -> number(value, 1..2999)
                "VOLUME" -> number(value, 0..30)
                "VOICE" -> value == "0" || value == "1"
                else -> false
            }
            "OLD" -> when (verb) {
                "GET" -> value == null
                "FACE" -> value in faces
                "TEXT" -> value != null && value.length in 1..39 &&
                    value.all { it.code in 32..126 }
                "CONTRAST" -> number(value, 8..255)
                "INVERT", "PAUSE" -> value == "0" || value == "1"
                else -> false
            }
            else -> false
        }
        if (!valid) return null
        return "$module,$id,$verb" + (value?.let { ",$it" } ?: "")
    }

    fun parseReply(frame: String): Reply? {
        if (frame.length > 124) return null
        val f = frame.split(',')
        if (f.size < 3 || !validId(f[1]) || f[2] !in setOf("OK", "ACCEPTED", "REJECTED")) return null
        fun int(i: Int, range: IntRange): Int? = f[i].toIntOrNull()?.takeIf { it in range }
        fun bit(i: Int): Boolean? = when (f[i]) { "0" -> false; "1" -> true; else -> null }
        return when (f[0]) {
            "AUD" -> {
                if (f.size != 11 || f[6] !in setOf("UNKNOWN", "STOPPED", "PLAYING", "PAUSED")) return null
                Reply(f[1], f[2], audio = AudioHardwareState(
                    bit(3) ?: return null, int(4, -1..1) ?: return null,
                    int(5, -1..65535) ?: return null, f[6],
                    int(7, -1..65535) ?: return null, int(8, -1..30) ?: return null,
                    bit(9) ?: return null, bit(10) ?: return null
                ))
            }
            "OLD" -> {
                if (f.size != 8 || f[2] == "ACCEPTED" || f[4] !in faces + setOf("telemetry", "text")) return null
                Reply(f[1], f[2], oled = OledHardwareState(
                    bit(3) ?: return null, f[4], int(5, 8..255) ?: return null,
                    bit(6) ?: return null, bit(7) ?: return null
                ))
            }
            else -> null
        }
    }
}
