package com.rgbcontroller.core.model

/**
 * A matching cached STATE is not acknowledgment of a new SET.
 * Require an actual new transport frame, power ON and effects stopped.
 */
object RgbAckPolicy {
    fun confirmed(
        requested: ColorUtils.RgbColor,
        submittedAtRevision: Long,
        receivedRevision: Long,
        actual: RgbDeviceState?
    ): Boolean =
        receivedRevision > submittedAtRevision &&
            RgbReadback.confirmsSet(
                requested.r, requested.g, requested.b, requested.brightness, actual
            )
}
