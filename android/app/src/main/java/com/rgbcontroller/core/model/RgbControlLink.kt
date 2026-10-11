package com.rgbcontroller.core.model

/**
 * UI contract shared by Bluetooth and Wi-Fi. Only the transport changes:
 * SET / ON / OFF / FX commands and STATE parsing remain identical.
 * A disconnected transport must never silently fall back to another one.
 */
data class RgbControlLink(
    val transport: String,
    val ready: Boolean,
    val state: RgbDeviceState?,
    val sendCommand: (String) -> Unit
)
