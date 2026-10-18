package com.rgbcontroller.core.ble

import com.rgbcontroller.core.model.RgbDeviceCommand

/**
 * GATT discovery/notification readiness is NOT permission to write an actuator.
 * Only firmware-confirmed owner authorization permits RGB mutations.
 */
object BleRgbPermission {
    fun permits(gattReady: Boolean, ownerState: BleSecurityState, command: String): Boolean =
        gattReady && RgbDeviceCommand.allowed(command) &&
            (command == "GET" || ownerState == BleSecurityState.Authorized)
}
