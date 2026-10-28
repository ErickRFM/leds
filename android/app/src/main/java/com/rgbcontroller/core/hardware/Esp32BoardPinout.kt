package com.rgbcontroller.core.hardware

/** Audited classic ESP32-D0WD-V3 lab profile; NOT a runtime pin discovery API. */
enum class PinKind { LAB_OUTPUT, RGB_RESERVED, ADC1_INPUT, SYSTEM_RESERVED }
data class BoardPin(val number: Int, val kind: PinKind, val note: String)

object Esp32BoardPinout {
    // Only 18/19 are available as general-purpose lab outputs in the wired Compi station.
    val manualOutputs = setOf(18, 19)
    private val inputOnly = setOf(34, 35, 36, 39)
    // GPIO32 belongs to LDR; GPIO33 belongs to DFPlayer BUSY.
    private val adc1 = setOf(34, 35, 36, 39)
    val pins = listOf(
        BoardPin(18, PinKind.LAB_OUTPUT, "Digital, salida temporal"),
        BoardPin(19, PinKind.LAB_OUTPUT, "Digital y experimentos"),
        BoardPin(21, PinKind.SYSTEM_RESERVED, "OLED SDA: GPIO reservado"),
        BoardPin(22, PinKind.SYSTEM_RESERVED, "OLED SCL: GPIO reservado"),
        BoardPin(23, PinKind.SYSTEM_RESERVED, "Botón de voz: GPIO reservado"),
        BoardPin(25, PinKind.RGB_RESERVED, "Verde RGB: no reclamar"),
        BoardPin(26, PinKind.RGB_RESERVED, "Azul RGB: no reclamar"),
        BoardPin(27, PinKind.RGB_RESERVED, "Rojo RGB: no reclamar"),
        BoardPin(32, PinKind.SYSTEM_RESERVED, "LDR AO: GPIO reservado"),
        BoardPin(33, PinKind.SYSTEM_RESERVED, "DFPlayer BUSY: GPIO reservado"),
        BoardPin(34, PinKind.ADC1_INPUT, "ADC1, solo entrada"),
        BoardPin(35, PinKind.ADC1_INPUT, "ADC1, solo entrada"),
        BoardPin(36, PinKind.ADC1_INPUT, "ADC1, solo entrada"),
        BoardPin(39, PinKind.ADC1_INPUT, "ADC1, solo entrada"),
        BoardPin(13, PinKind.SYSTEM_RESERVED, "DFPlayer TX: UART2 RX"),
        BoardPin(14, PinKind.SYSTEM_RESERVED, "DFPlayer RX: UART2 TX"),
        BoardPin(16, PinKind.SYSTEM_RESERVED, "DHT11 DATA: GPIO reservado"),
        BoardPin(0, PinKind.SYSTEM_RESERVED, "Arranque: protegido"),
        BoardPin(6, PinKind.SYSTEM_RESERVED, "Flash interna: prohibido"),
        BoardPin(7, PinKind.SYSTEM_RESERVED, "Flash interna: prohibido"),
        BoardPin(8, PinKind.SYSTEM_RESERVED, "Flash interna: prohibido"),
        BoardPin(9, PinKind.SYSTEM_RESERVED, "Flash interna: prohibido"),
        BoardPin(10, PinKind.SYSTEM_RESERVED, "Flash interna: prohibido"),
        BoardPin(11, PinKind.SYSTEM_RESERVED, "Flash interna: prohibido"),
        BoardPin(12, PinKind.SYSTEM_RESERVED, "Arranque: protegido")
    )

    fun canWrite(pin: Int) = pin in manualOutputs
    // Preserve the existing digital input allowlist until firmware capability discovery.
    fun canRead(pin: Int) = canWrite(pin) || pin in inputOnly
    fun canReadAdc1(pin: Int) = pin in adc1
}
