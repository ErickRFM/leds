package com.rgbcontroller.core.model

/** True only when device-readback STATE agrees exactly with a requested SET. */
object RgbReadback {
    fun confirmsSet(
        requestedRed: Int, requestedGreen: Int, requestedBlue: Int,
        requestedBrightness: Int, actual: RgbDeviceState?
    ): Boolean =
        actual != null && actual.power && actual.effect == "NONE" &&
        actual.red == requestedRed && actual.green == requestedGreen &&
        actual.blue == requestedBlue && actual.brightness == requestedBrightness
}
