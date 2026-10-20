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
            description = "Fotorresistencia LDR en divisor de tensión 10k (ADC1, GPIO36)",
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
        )
    )

    fun get(id: String): HardwareComponentDescriptor? = components.find { it.id == id }

    /**
     * Translates a firmware component id (what `GET /v2/components/status`
     * reports, e.g. `sensor.soil_moisture` or `led.rgb`) into this catalog's id
     * (`soil_moisture`, `led_rgb`). Without this, an enabled firmware module
     * would still be listed as an undetected template. Unknown ids are returned
     * unchanged so a new firmware module is never silently hidden.
     */
    fun catalogIdForFirmwareId(firmwareId: String): String = when (firmwareId) {
        "sensor.dht11" -> "dht11"
        "sensor.dht22" -> "dht22"
        "sensor.soil_moisture" -> "soil_moisture"
        "sensor.ldr_light" -> "ldr_light"
        "led.rgb" -> "led_rgb"
        "display.ssd1306" -> "ssd1306_oled"
        else -> firmwareId
    }
}
