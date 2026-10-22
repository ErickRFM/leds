package com.rgbcontroller.core.model

/**
 * Non-secret readback from V4 diagnostic API.
 * A JSON capability claim on legacy HTTP is NOT cryptographic
 * attestation or an authenticated bond. Do not derive permissions from it.
 */
data class DeviceIdentityInfo(
    val schemaVersion: Int,
    val deviceId: String,
    val secureProvisioning: Boolean,
    val legacyBleWriteUnauthenticated: Boolean,
    val ownerBound: Boolean
) {
    val validId: Boolean get() =
        schemaVersion == 4 && Regex("[0-9A-F]{16}").matches(deviceId)

    val report: String get() = when {
        !validId -> "Respuesta de identidad incompatible o corrupta"
        !secureProvisioning || legacyBleWriteUnauthenticated || !ownerBound ->
            "Dispositivo V4 identificado; vinculación segura NO disponible"
        else -> "El firmware declara capacidades de seguridad: requieren verificación física y criptográfica"
    }

    // Deliberately no isAuthorized property based on untrusted JSON.
}
