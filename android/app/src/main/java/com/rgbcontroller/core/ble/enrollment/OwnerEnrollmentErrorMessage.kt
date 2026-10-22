package com.rgbcontroller.core.ble.enrollment

/**
 * User-facing recovery guidance for firmware owner-enrollment rejections.
 * Errors do not grant authorization and never expose or persist the pairing code.
 */
fun ownerEnrollmentErrorMessage(reason: String): String = when (reason) {
    "bad_code" ->
        "El código no coincide con la ventana física actual. Mantén BOOT 3 s y " +
            "copia el ÚLTIMO código de 8 caracteres del monitor USB (115200) " +
            "antes de 2 minutos. Las letras A–F pueden ser mayúsculas o minúsculas."
    "not_in_window" ->
        "El emparejamiento físico no está abierto. Mantén BOOT 3 s y " +
            "espera el mensaje PHYSICAL PAIRING WINDOW en el monitor USB."
    "window_expired" ->
        "El código temporal caducó. Mantén BOOT 3 s para generar uno nuevo " +
            "y úsalo antes de 2 minutos."
    "already_enrolled" ->
        "El ESP32 ya tiene propietario. Si es tu teléfono, vuelve a conectar " +
            "para revalidar la sesión. Si perdiste la clave, utiliza recuperación física."
    "not_enrolled" ->
        "El ESP32 todavía no tiene propietario registrado. Abre el " +
            "emparejamiento con BOOT 3 s e introduce el código USB."
    "not_in_recovery" ->
        "No hay recuperación física abierta. Si necesitas reemplazar el " +
            "propietario, abre RECOVERY con BOOT 3 s antes de confirmar."
    "recovery_requires_physical" ->
        "La recuperación requiere presionar físicamente BOOT en el ESP32."
    "bad_proof", "bad_challenge", "replay" ->
        "No se pudo verificar la identidad criptográfica. Reconecta Compi " +
            "y verifica que sea el mismo dispositivo; no se habilitó control."
    else -> "El ESP32 rechazó la autorización ($reason). Consulta el monitor USB."
}
