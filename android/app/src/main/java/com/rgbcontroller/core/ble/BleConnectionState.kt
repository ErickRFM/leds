package com.rgbcontroller.core.ble

sealed interface BleConnectionState {
    data object Disconnected : BleConnectionState
    data object Scanning : BleConnectionState
    data object Connecting : BleConnectionState
    data object Discovering : BleConnectionState
    data object Ready : BleConnectionState
    data class Error(val message: String) : BleConnectionState
}
