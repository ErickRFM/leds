package com.rgbcontroller.core.model

/**
 * One transport is not one capability. Each row records what the firmware
 * actually accepts today, proven by the frame or endpoint named in the row, so
 * the UI can never promise more than the protocol delivers.
 */
enum class CapabilitySupport { AVAILABLE, LIMITED, UNAVAILABLE }

data class CapabilityRow(
    val id: String,
    val group: String,
    val label: String,
    val ble: CapabilitySupport,
    val wifi: CapabilitySupport,
    /** Frame prefix the ESP32 accepts over GATT; null when BLE cannot do it. */
    val bleProof: String? = null,
    /** HTTP path the SoftAP lab server serves; null when Wi-Fi cannot do it. */
    val wifiProof: String? = null,
    val note: String? = null
)

object TransportCapabilityMatrix {
    const val GROUP_LED = "LED y ambiente"
    const val GROUP_COMPONENTS = "Sensores y componentes"
    const val GROUP_MULTIMEDIA = "Multimedia y robótica"
    const val GROUP_ADVANCED = "Automatización y laboratorio"
    const val GROUP_BLOCKED = "Bloqueado en ambos"

    val rows: List<CapabilityRow> = listOf(
        CapabilityRow(
            id = "rgb_color", group = GROUP_LED, label = "Encender, apagar y fijar color o brillo",
            ble = CapabilitySupport.AVAILABLE, wifi = CapabilitySupport.AVAILABLE,
            bleProof = "SET,", wifiProof = "/v2/led/command"
        ),
        CapabilityRow(
            id = "rgb_effects", group = GROUP_LED, label = "Efectos RGB (FX)",
            ble = CapabilitySupport.AVAILABLE, wifi = CapabilitySupport.AVAILABLE,
            bleProof = "FX,", wifiProof = "/v2/led/command"
        ),
        CapabilityRow(
            id = "environmental", group = GROUP_LED, label = "Modo ambiental por bandas de temperatura",
            ble = CapabilitySupport.AVAILABLE, wifi = CapabilitySupport.AVAILABLE,
            bleProof = "ENV,", wifiProof = "/v2/environment/rgb/start",
            note = "El estado solo queda confirmado cuando el ESP32 responde ENV,STATE."
        ),
        CapabilityRow(
            id = "sensor_telemetry", group = GROUP_COMPONENTS,
            label = "Temperatura, humedad y luz leídas del firmware",
            ble = CapabilitySupport.AVAILABLE, wifi = CapabilitySupport.AVAILABLE,
            bleProof = "SNP,", wifiProof = "/v2/components/telemetry"
        ),
        CapabilityRow(
            id = "component_status", group = GROUP_COMPONENTS,
            label = "Pines y estado real de cada componente",
            ble = CapabilitySupport.AVAILABLE, wifi = CapabilitySupport.AVAILABLE,
            bleProof = "CPN,", wifiProof = "/v2/components/status"
        ),
        CapabilityRow(
            id = "pin_rewire", group = GROUP_COMPONENTS, label = "Reasignar pines sin reiniciar",
            ble = CapabilitySupport.LIMITED, wifi = CapabilitySupport.AVAILABLE,
            bleProof = "PIN,", wifiProof = "/v2/components/apply",
            note = "Por Bluetooth el firmware acepta dht11, ldr_light y robotics_crane; " +
                "por Wi-Fi cubre todo el catálogo aplicable, incluidos OLED, RGB y otros sensores."
        ),
        CapabilityRow(
            id = "ldr_calibration", group = GROUP_COMPONENTS, label = "Calibrar el LDR en oscuro y claridad",
            ble = CapabilitySupport.AVAILABLE, wifi = CapabilitySupport.AVAILABLE,
            bleProof = "LDR,", wifiProof = "/v2/components/ldr/calibrate"
        ),
        CapabilityRow(
            id = "gpio_read", group = GROUP_COMPONENTS, label = "Consultar el estado de un pin",
            ble = CapabilitySupport.LIMITED, wifi = CapabilitySupport.AVAILABLE,
            bleProof = "GPIO_STATUS", wifiProof = "/v2/gpio/read",
            note = "La característica GPIO_STATUS de Bluetooth es informativa y nunca autoriza una escritura."
        ),
        CapabilityRow(
            id = "gpio_write", group = GROUP_COMPONENTS, label = "Escribir o reiniciar pines sueltos",
            ble = CapabilitySupport.UNAVAILABLE, wifi = CapabilitySupport.AVAILABLE,
            wifiProof = "/v2/gpio/write",
            note = "No hay verbo Bluetooth para GPIO crudo: el firmware responde UNKNOWN."
        ),
        CapabilityRow(
            id = "adc_raw", group = GROUP_COMPONENTS, label = "Lectura analógica cruda de un pin",
            ble = CapabilitySupport.UNAVAILABLE, wifi = CapabilitySupport.AVAILABLE,
            wifiProof = "/v2/adc/read"
        ),
        CapabilityRow(
            id = "crane_motion", group = GROUP_MULTIMEDIA, label = "Grúa: ejes, presets, joystick y parada",
            ble = CapabilitySupport.AVAILABLE, wifi = CapabilitySupport.UNAVAILABLE,
            bleProof = "CRN,",
            note = "Por Wi-Fi la grúa solo admite reasignar sus pines."
        ),
        CapabilityRow(
            id = "audio", group = GROUP_MULTIMEDIA, label = "DFPlayer: reproducir, pausar, volumen y voz",
            ble = CapabilitySupport.AVAILABLE, wifi = CapabilitySupport.AVAILABLE,
            bleProof = "AUD,", wifiProof = "/v2/audio/"
        ),
        CapabilityRow(
            id = "oled", group = GROUP_MULTIMEDIA, label = "OLED: cara, texto, contraste e inversión",
            ble = CapabilitySupport.AVAILABLE, wifi = CapabilitySupport.AVAILABLE,
            bleProof = "OLD,", wifiProof = "/v2/display/oled"
        ),
        CapabilityRow(
            id = "automation", group = GROUP_ADVANCED, label = "Automatización acotada por intervalo y ciclos",
            ble = CapabilitySupport.UNAVAILABLE, wifi = CapabilitySupport.AVAILABLE,
            wifiProof = "/v2/automation/start",
            note = "Requiere que el firmware confirme soporte; sin esa confirmación la app la mantiene bloqueada."
        ),
        CapabilityRow(
            id = "script", group = GROUP_ADVANCED, label = "Programas y secuencias guardadas",
            ble = CapabilitySupport.UNAVAILABLE, wifi = CapabilitySupport.AVAILABLE,
            wifiProof = "/v2/script/run"
        ),
        CapabilityRow(
            id = "lab_checks", group = GROUP_ADVANCED, label = "Autocomprobación de parpadeo y secuencia",
            ble = CapabilitySupport.UNAVAILABLE, wifi = CapabilitySupport.AVAILABLE,
            wifiProof = "/v2/lab/blink"
        ),
        CapabilityRow(
            id = "wifi_config", group = GROUP_ADVANCED, label = "Configurar la red del ESP32",
            ble = CapabilitySupport.UNAVAILABLE, wifi = CapabilitySupport.AVAILABLE,
            wifiProof = "/v2/wifi/connect"
        ),
        CapabilityRow(
            id = "identity", group = GROUP_ADVANCED, label = "Identidad, versión y diagnóstico del dispositivo",
            ble = CapabilitySupport.AVAILABLE, wifi = CapabilitySupport.AVAILABLE,
            bleProof = "PUBLIC_ID", wifiProof = "/v2/identity",
            note = "Identificar el equipo no autoriza mandos: la autorización viene de la sesión de propietario."
        ),
        CapabilityRow(
            id = "ota", group = GROUP_BLOCKED, label = "Actualizar el firmware por aire",
            ble = CapabilitySupport.UNAVAILABLE, wifi = CapabilitySupport.UNAVAILABLE,
            note = "Bloqueado mientras no exista imagen firmada, rollback y recuperación física."
        )
    )

    fun rowsByGroup(): List<Pair<String, List<CapabilityRow>>> =
        rows.groupBy { it.group }.entries.map { it.key to it.value }

    /** A row may only claim support when it names the frame or endpoint that proves it. */
    fun unsupportedClaims(): List<String> = rows.filter { row ->
        (row.ble != CapabilitySupport.UNAVAILABLE && row.bleProof.isNullOrBlank()) ||
            (row.wifi != CapabilitySupport.UNAVAILABLE && row.wifiProof.isNullOrBlank())
    }.map { it.id }

    /** Proof tokens must be frames and routes the transports really use, never prose. */
    fun proofViolations(): List<String> = rows.flatMap { row ->
        buildList {
            if (row.ble == CapabilitySupport.UNAVAILABLE && row.bleProof != null) {
                add("${row.id}: Bluetooth bloqueado no puede citar marco")
            }
            if (row.wifi == CapabilitySupport.UNAVAILABLE && row.wifiProof != null) {
                add("${row.id}: Wi-Fi bloqueado no puede citar endpoint")
            }
        }
    }
}
