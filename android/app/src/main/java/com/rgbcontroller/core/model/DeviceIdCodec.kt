package com.rgbcontroller.core.model

/** Non-secret 16-character public ESP32 ID. Never authorizes a command. */
object DeviceIdCodec {
    private val format = Regex("[0-9A-F]{16}")
    fun accept(frame: String): String? = frame.takeIf { format.matches(it) }
}
