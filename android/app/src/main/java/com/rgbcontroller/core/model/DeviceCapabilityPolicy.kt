package com.rgbcontroller.core.model

/** A feature is allowed only when the transport proves its required properties.
 * Public MAC / device ID, BLE GATT readiness and proximity are never ownership proof.
 */
enum class CompiOperation {
    READ_RGB, SET_RGB, EFFECT_RGB, READ_GPIO, WRITE_GPIO, READ_ADC,
    RUN_GPIO_SCRIPT, RUN_RGB_SCRIPT, CONFIGURE_WIFI, AUTOMATION, FIRMWARE_UPDATE
}
enum class CompiChannel { BLE_V1, WIFI_LAB, AUTHENTICATED_BLE }
data class OperationPermission(val allowed: Boolean, val explanation: String)

object DeviceCapabilityPolicy {
    fun check(
        operation: CompiOperation, channel: CompiChannel,
        connected: Boolean, debugBuild: Boolean = true,
        gpioTelemetryAvailable: Boolean = false
    ): OperationPermission {
        if (!connected) return OperationPermission(false, "Conecta el ESP32 antes de realizar esta operación.")
        if (operation == CompiOperation.FIRMWARE_UPDATE) return OperationPermission(
            false, "Firmware OTA bloqueado: requiere imagen firmada, rollback y recuperación física.")
        if (operation == CompiOperation.AUTOMATION) return OperationPermission(
            false, "Automatizaciones aún no integradas y verificadas en el firmware de main.")
        return when (channel) {
            CompiChannel.BLE_V1 -> {
                val rgb = operation in setOf(
                    CompiOperation.READ_RGB, CompiOperation.SET_RGB, CompiOperation.EFFECT_RGB
                )
                val gpioRead = operation == CompiOperation.READ_GPIO && gpioTelemetryAvailable
                OperationPermission(rgb || gpioRead,
                    if (rgb || gpioRead) "Disponible por Bluetooth experimental."
                    else "BLE V1 no autoriza esta operación. Usa Wi-Fi local protegido o espera BLE seguro.")
            }
            CompiChannel.WIFI_LAB -> {
                OperationPermission(debugBuild,
                    if (debugBuild) "Disponible por SoftAP de laboratorio con clave válida."
                    else "Solo permitido en compilación de laboratorio.")
            }
            CompiChannel.AUTHENTICATED_BLE -> {
                // Future transport type is NOT proof that a secure session exists.
                // V10 firmware has no cryptographic enrollment/verifier. DENY all.
                OperationPermission(false,
                    "Sin autorización BLE verificable en firmware. Requiere enrolamiento criptográfico.")
            }
        }
    }
}
