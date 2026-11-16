package com.rgbcontroller.core.components.registry

import com.rgbcontroller.core.components.model.*

object ComponentCatalog {
    val components = listOf(
        HardwareComponentDescriptor(
            id = "dht11",
            name = "DHT11",
            type = ComponentType.SENSOR,
            interfaces = setOf(HardwareInterface.GPIO, HardwareInterface.DIGITAL),
            capabilities = setOf("humidity", "temperature", "environmental"),
            requiredPins = setOf(16),
            description = "Sensor DHT11 de humedad y temperatura, DATA en GPIO16 por defecto",
            category = "sensors"
        ),
        HardwareComponentDescriptor(
            id = "dht22",
            name = "DHT22",
            type = ComponentType.SENSOR,
            interfaces = setOf(HardwareInterface.GPIO, HardwareInterface.DIGITAL),
            capabilities = setOf("humidity", "temperature", "environmental"),
            requiredPins = setOf(32),
            description = "Sensor de temperatura y humedad ambiental DHT22 (GPIO digital)",
            category = "sensors"
        ),
        HardwareComponentDescriptor(
            id = "soil_moisture",
            name = "Sensor de Humedad del Suelo",
            type = ComponentType.SENSOR,
            interfaces = setOf(HardwareInterface.ADC),
            capabilities = setOf("soil_moisture", "analog", "calibrated"),
            requiredPins = setOf(32, 33, 34, 35, 36, 39),
            description = "Sensor capacitivo de humedad del suelo (ADC1)",
            category = "sensors"
        ),
        HardwareComponentDescriptor(
            id = "ldr_light",
            name = "Sensor de Luz LDR",
            type = ComponentType.SENSOR,
            interfaces = setOf(HardwareInterface.ADC),
            capabilities = setOf("ambient_light", "analog", "calibrated"),
            requiredPins = setOf(32, 33, 34, 35, 36, 39),
            description = "Sensor LDR conectado a AO por ADC1 GPIO32; calibración con oscuridad y luz",
            category = "sensors"
        ),
        HardwareComponentDescriptor(
            id = "led_rgb",
            name = "LED RGB",
            type = ComponentType.LIGHTING,
            interfaces = setOf(HardwareInterface.PWM, HardwareInterface.GPIO),
            capabilities = setOf("rgb", "brightness", "effects", "real_state"),
            requiredPins = setOf(27, 25, 26),
            description = "LED RGB de 3 canales con PWM",
            category = "lighting"
        ),
        HardwareComponentDescriptor(
            id = "ssd1306_oled",
            name = "OLED SSD1306",
            type = ComponentType.DISPLAY,
            interfaces = setOf(HardwareInterface.I2C),
            capabilities = setOf("display", "text", "i2c"),
            requiredPins = emptySet(),
            description = "Pantalla OLED SSD1306 I2C 128x64",
            category = "displays"
        ),
        HardwareComponentDescriptor(
            id = "analog_sensor",
            name = "Sensor analógico",
            type = ComponentType.SENSOR,
            interfaces = setOf(HardwareInterface.ADC),
            capabilities = setOf("analog", "raw"),
            requiredPins = emptySet(),
            description = "Sensor genérico analógico ADC1 · requiere perfil de firmware",
            category = "sensors"
        ),
        HardwareComponentDescriptor(
            id = "digital_sensor",
            name = "Sensor digital",
            type = ComponentType.SENSOR,
            interfaces = setOf(HardwareInterface.DIGITAL, HardwareInterface.GPIO),
            capabilities = setOf("digital", "input"),
            requiredPins = emptySet(),
            description = "Sensor digital genérico · requiere perfil de firmware",
            category = "sensors"
        ),
        HardwareComponentDescriptor(
            id = "actuator_relay",
            name = "Relé / actuador",
            type = ComponentType.ACTUATOR,
            interfaces = setOf(HardwareInterface.GPIO, HardwareInterface.DIGITAL),
            capabilities = setOf("actuator", "control"),
            requiredPins = emptySet(),
            description = "Actuador digital · requiere perfil de firmware",
            category = "actuators"
        ),
        HardwareComponentDescriptor(
            id = "robotics_crane",
            name = "Compi Robotics · Grúa de dos ejes",
            type = ComponentType.CONTROLLER,
            interfaces = setOf(HardwareInterface.PWM, HardwareInterface.DIGITAL, HardwareInterface.GPIO),
            capabilities = setOf("robotics", "servo", "dual_axis", "digital_joystick", "bluetooth_control"),
            requiredPins = emptySet(),
            description = "Dos servomotores SG90 y joystick digital de cinco direcciones; pines asignables en la app (7 señales) con fuente externa de 5 V y pull-ups externos en las entradas.",
            category = "robotics"
        ),
        HardwareComponentDescriptor(
            id = "dfplayer_mp3",
            name = "Reproductor MP3 DFPlayer Mini",
            type = ComponentType.ACTUATOR,
            interfaces = setOf(HardwareInterface.GPIO, HardwareInterface.DIGITAL),
            capabilities = setOf("audio", "mp3", "voice", "uart"),
            requiredPins = setOf(13, 14),
            description = "Módulo reproductor MP3 DFPlayer Mini por UART2 (ESP32 RX GPIO13, TX GPIO14)",
            category = "audio"
        )
    )

    fun get(id: String): HardwareComponentDescriptor? = components.find { it.id == id }

    /**
     * The only app-side table of firmware module ids. The ESP32 reports these
     * dotted ids in `/v2/components/status` and in the BLE snapshot, while the
     * HTTP and BLE rewire endpoints accept this catalog's underscore ids.
     * `scripts/check-contracts.py` refuses any entry the firmware allow-list
     * does not accept, so the two sides cannot drift again.
     */
    private val firmwareIds: Map<String, String> = mapOf(
        "dht11" to "sensor.dht11",
        "dht22" to "sensor.dht22",
        "soil_moisture" to "sensor.soil_moisture",
        "ldr_light" to "sensor.ldr_light",
        "led_rgb" to "led.rgb",
        "ssd1306_oled" to "display.ssd1306",
        "dfplayer_mp3" to "audio.dfplayer",
        "robotics_crane" to "robotics.crane.2axis"
    )

    /** Firmware id for a catalog id, or null when the ESP32 has no such driver. */
    fun firmwareIdFor(catalogId: String): String? = firmwareIds[catalogId]

    /**
     * Translates a firmware component id (what `GET /v2/components/status`
     * reports, e.g. `sensor.soil_moisture` or `led.rgb`) into this catalog's id
     * (`soil_moisture`, `led_rgb`). Without this, an enabled firmware module
     * would still be listed as an undetected template. Unknown ids are returned
     * unchanged so a new firmware module is never silently hidden.
     */
    fun catalogIdForFirmwareId(firmwareId: String): String =
        firmwareIds.entries.firstOrNull { it.value == firmwareId }?.key ?: firmwareId

    /**
     * Components the firmware `/v2/components/apply` can rewire, with the GPIO
     * signals each transaction must carry. This is deliberately narrower than
     * `firmwareIds`: the DFPlayer reports status but has no runtime rewiring
     * driver, and offering it would only ever produce a firmware rejection.
     */
    val rewireSignals: Map<String, Int> = mapOf(
        "dht11" to 1,
        "dht22" to 1,
        "soil_moisture" to 1,
        "ldr_light" to 1,
        "led_rgb" to 3,
        "ssd1306_oled" to 2,
        "robotics_crane" to 7
    )
}
