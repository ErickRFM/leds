package com.rgbcontroller.core.ble

/**
 * Transport-level BLE owner-authorization state.
 *
 * A peer is never authorized merely by connecting, pairing, bonding or
 * encrypting the link. The firmware is the sole authority: it grants
 * [Authorized] only after the exact provisioned owner key is presented over an
 * encrypted link, and it revokes unconditionally on disconnect.
 */
enum class BleSecurityState {
    /** No link, or the link was closed; any prior authorization is revoked. */
    Disconnected,

    /** Connected but not authorized (no owner key configured or not yet proven). */
    Unauthorized,

    /** Pairing/bonding handshake in progress; grants no control. */
    Pairing,

    /** Bonded/encrypted link available, owner key not yet confirmed. */
    Bonded,

    /** Owner key sent; awaiting the firmware confirmation frame. */
    Authorizing,

    /** Firmware confirmed the owner key; protected commands are accepted. */
    Authorized,

    /** Firmware rejected the owner key or pairing failed. */
    AuthorizationFailed,

    /** Authorization or pairing timed out. */
    Timeout,

    /** Session or owner has been revoked by the device. */
    Revoked,

    /** Physical gesture (BOOT button / recovery window) required on device. */
    RecoveryRequired
}
