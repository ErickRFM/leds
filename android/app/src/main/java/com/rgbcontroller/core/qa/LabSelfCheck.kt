package com.rgbcontroller.core.qa

import com.rgbcontroller.core.model.RgbDeviceState

data class LabSnapshot(
    val apiVersion: Int,
    val rgbFrame: String,
    val chip: String,
    val flashBytes: Long,
    val freeHeapBytes: Long,
    val nextOtaSlotBytes: Long,
    val otaSlotPresent: Boolean,
    val otaUploadEnabled: Boolean
)
data class LabCheck(val name: String, val passed: Boolean, val detail: String)

/**
 * API/firmware contract self-check only. PASS does NOT prove physical GPIO,
 * LED polarity, security, BLE authorization, Wi-Fi provisioning or OTA rollback.
 */
object LabSelfCheck {
    fun evaluate(snapshot: LabSnapshot): List<LabCheck> = listOf(
        LabCheck("Versión de API V2", snapshot.apiVersion == 2,
            "Recibido: " + snapshot.apiVersion),
        LabCheck("Estado RGB interpretable", RgbDeviceState.parse(snapshot.rgbFrame) != null,
            "Formato STATE V1 compatible"),
        LabCheck("Identidad de chip", snapshot.chip.startsWith("ESP32"),
            "Chip anunciado: " + snapshot.chip.take(24)),
        LabCheck("Flash detectada (4 MB)", snapshot.flashBytes == 4_194_304L,
            "Tamaño: " + snapshot.flashBytes + " B"),
        LabCheck("RAM reportada", snapshot.freeHeapBytes > 0L,
            "Disponible: " + snapshot.freeHeapBytes + " B"),
        LabCheck("Slot alternativo inspeccionado",
            snapshot.otaSlotPresent && snapshot.nextOtaSlotBytes > 0L,
            "Slot alternativo: " + snapshot.nextOtaSlotBytes + " B"),
        LabCheck("OTA deshabilitada en laboratorio", !snapshot.otaUploadEnabled,
            "No se deben subir binarios OTA no firmados")
    )

    fun report(snapshot: LabSnapshot): String {
        val checks = evaluate(snapshot)
        return buildString {
            appendLine("ESP32 CONTROL CENTER — AUTODIAGNÓSTICO DE API (LAB)")
            for (check in checks) {
                appendLine((if (check.passed) "[OK] " else "[FALLO] ") +
                    check.name + " — " + check.detail)
            }
            appendLine("Comprobaciones API: " + checks.count { it.passed } + "/" + checks.size)
            appendLine("NO VERIFICADO: hardware eléctrico, LEDs reales, BLE autorizado, OTA/rollback.")
            appendLine("NO SE INCLUYEN claves, contraseñas, SSID, MAC, ni IP personales.")
        }
    }
}
