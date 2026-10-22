package com.rgbcontroller.core.components.status

import com.rgbcontroller.core.components.model.ComponentRuntimeStatus

/**
 * Derived runtime state of the OLED SSD1306 display from the firmware's
 * component status (`GET /v2/components/status`). The three lifecycle stages
 * are kept deliberately separate so the UI never claims more than the firmware
 * proved:
 *
 *  - [detected]  : the firmware listed `display.ssd1306` at all (present and
 *    enabled in the running firmware).
 *  - [configured]: the module acquired SDA/SCL and ran its constructor
 *    ([ComponentRuntimeStatus.active] is true).
 *  - [operative] : the driver initialised the panel over I2C and the component
 *    is still `active`; this is the strongest claim the current contract allows.
 *
 * The status contract carries no display text and no present-confirmation flag,
 * so [OledRuntimeState] never carries invented content or a fake "showing" state.
 */
data class OledRuntimeState(
    val detected: Boolean,
    val configured: Boolean,
    val operative: Boolean,
    val state: String,
    val sda: Int?,
    val scl: Int?
)

object OledRuntime {
    /** Firmware component id emitted by `OledSsd1306Module::id()`. */
    const val FIRMWARE_ID = "display.ssd1306"

    /** Catalog id used by the Android `ComponentCatalog`. */
    const val CATALOG_ID = "ssd1306_oled"

    /**
     * Default I2C address (`OledSsd1306Module::kDefaultAddress`). The status
     * contract does not carry the address, so it is surfaced as a declared
     * default, always subject to the real board configuration.
     */
    const val DEFAULT_ADDRESS = 0x3C

    fun from(status: ComponentRuntimeStatus?): OledRuntimeState {
        if (status == null) {
            return OledRuntimeState(
                detected = false,
                configured = false,
                operative = false,
                state = "absent",
                sda = null,
                scl = null
            )
        }
        val configured = status.active
        return OledRuntimeState(
            detected = true,
            configured = configured,
            operative = configured && status.state == "active",
            state = status.state,
            sda = status.pins.getOrNull(0),
            scl = status.pins.getOrNull(1)
        )
    }
}
