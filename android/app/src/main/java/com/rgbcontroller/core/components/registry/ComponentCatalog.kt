package com.rgbcontroller.core.components.registry

import com.rgbcontroller.core.components.model.*

object ComponentCatalog {
    val components = listOf(
        HardwareComponentDescriptor(
            id = "dht22",
            name = "DHT22",
            type = ComponentType.SENSOR,
            interfaces = setOf(HardwareInterface.GPIO, HardwareInterface.DIGITAL),
            capabilities = setOf("humidity", "temperature", "environmental"),
            requiredPins = setOf(2),
            description = "Sensor de temperatura y humedad ambiental DHT22",
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
            name = "Sensor Analógico",
            type = ComponentType.SENSOR,
            interfaces = setOf(HardwareInterface.ADC),
            capabilities = setOf("analog", "raw"),
            requiredPins = emptySet(),
            description = "Sensor genérico analógico ADC1",
            category = "sensors"
        ),
        HardwareComponentDescriptor(
            id = "digital_sensor",
            name = "Sensor Digital",
            type = ComponentType.SENSOR,
            interfaces = setOf(HardwareInterface.DIGITAL, HardwareInterface.GPIO),
            capabilities = setOf("digital", "input"),
            requiredPins = emptySet(),
            description = "Sensor digital genérico",
            category = "sensors"
        ),
        HardwareComponentDescriptor(
            id = "actuator_relay",
            name = "Relé/Actuador",
            type = ComponentType.ACTUATOR,
            interfaces = setOf(HardwareInterface.GPIO, HardwareInterface.DIGITAL),
            capabilities = setOf("actuator", "control"),
            requiredPins = emptySet(),
            description = "Actuador digital",
            category = "actuators"
        )
    )

    fun get(id: String): HardwareComponentDescriptor? = components.find { it.id == id }
}
