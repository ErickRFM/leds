package com.rgbcontroller.core.ble

import com.rgbcontroller.core.model.RgbDeviceCommand

/**
 * Prevent a connected/bonded GATT link from sending protected RGB writes.
 * Owner enrollment is handled by the private protocol queue, never this API.
 */
object BleRgbControlPolicy {
    fun allowed(
        connection: BleConnectionState,
        security: BleSecurityState,
        command: String
    ): Boolean =
        connection == BleConnectionState.Ready &&
            security == BleSecurityState.Authorized &&
            !command.startsWith("AUTH ") &&
            RgbDeviceCommand.allowed(command)
}
