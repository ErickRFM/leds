package com.rgbcontroller.core.hardware

/** Audited classic ESP32-D0WD-V3 lab profile; NOT a runtime pin discovery API. */
enum class PinKind { LAB_OUTPUT, RGB_RESERVED, ADC1_INPUT, SYSTEM_RESERVED }
data class BoardPin(val number: Int, val kind: PinKind, val note: String)

object Esp32BoardPinout {
    val manualOutputs = setOf(18, 19, 21, 22, 23)
    private val inputOnly = setOf(34, 35, 36, 39)
    private val adc1 = setOf(32, 33, 34, 35, 36, 39)
    val pins = listOf(
        BoardPin(18, PinKind.LAB_OUTPUT, "Digital, salida temporal"),
        BoardPin(19, PinKind.LAB_OUTPUT, "Digital y experimentos"),
        BoardPin(21, PinKind.LAB_OUTPUT, "Digital, posible SDA I2C futuro"),
        BoardPin(22, PinKind.LAB_OUTPUT, "Digital, posible SCL I2C futuro"),
        BoardPin(23, PinKind.LAB_OUTPUT, "Digital, salida temporal"),
        BoardPin(25, PinKind.RGB_RESERVED, "Verde RGB: no reclamar"),
        BoardPin(26, PinKind.RGB_RESERVED, "Azul RGB: no reclamar"),
        BoardPin(27, PinKind.RGB_RESERVED, "Rojo RGB: no reclamar"),
        BoardPin(32, PinKind.ADC1_INPUT, "ADC1, pendiente de prueba física"),
        BoardPin(33, PinKind.ADC1_INPUT, "ADC1, pendiente de prueba física"),
        BoardPin(34, PinKind.ADC1_INPUT, "ADC1, solo entrada"),
        BoardPin(35, PinKind.ADC1_INPUT, "ADC1, solo entrada"),
        BoardPin(36, PinKind.ADC1_INPUT, "ADC1, solo entrada"),
        BoardPin(39, PinKind.ADC1_INPUT, "ADC1, solo entrada"),
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
