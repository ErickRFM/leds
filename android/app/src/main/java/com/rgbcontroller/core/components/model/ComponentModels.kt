package com.rgbcontroller.core.components.model

enum class ComponentType {
    SENSOR,
    ACTUATOR,
    DISPLAY,
    CONTROLLER,
    LIGHTING,
    COMMUNICATION,
    INPUT,
    OUTPUT
}

enum class HardwareInterface {
    GPIO,
    ADC,
    I2C,
    SPI,
    PWM,
    ONE_WIRE,
    DIGITAL
}

enum class ComponentState {
    AVAILABLE,
    NOT_CONFIGURED,
    CONFIGURING,
    CONFIGURED,
    INITIALIZING,
    ACTIVE,
    NO_DATA,
    ERROR,
    INCOMPATIBLE,
    DISCONNECTED
}

data class HardwareComponentDescriptor(
    val id: String,
    val name: String,
    val type: ComponentType,
    val interfaces: Set<HardwareInterface>,
    val capabilities: Set<String>,
    val requiredPins: Set<Int> = emptySet(),
    val description: String = "",
    val supported: Boolean = true,
    val category: String = "general"
)

data class ComponentConfig(
    val pin: Int? = null,
    val i2cAddress: Int? = null,
    val adcPin: Int? = null,
    val params: Map<String, String> = emptyMap()
)

data class ConfiguredComponent(
    val id: String,
    val descriptor: HardwareComponentDescriptor,
    val config: ComponentConfig = ComponentConfig(),
    val state: ComponentState = ComponentState.NOT_CONFIGURED,
    val order: Int = 0,
    val enabled: Boolean = true,
    val instanceId: String = "${descriptor.id}_${System.currentTimeMillis()}"
)
