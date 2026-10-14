package com.rgbcontroller.core.components.ui

import com.rgbcontroller.core.components.model.ConfiguredComponent
import com.rgbcontroller.core.components.widgets.*
    component: ConfiguredComponent,
    modifier: Modifier = Modifier
) {
    when (component.descriptor.id) {
        "dht22" -> HumidityTemperatureWidget(
            humidity = null,
            temperature = null,
            hasData = component.state == com.rgbcontroller.core.components.model.ComponentState.ACTIVE,
            modifier = modifier
        )
        "soil_moisture" -> SoilMoistureWidget(
            moisturePercent = null,
            rawAdc = null,
            calibrated = false,
            modifier = modifier
        )
        "led_rgb" -> RgbLedWidget(
            red = 0,
            green = 0,
            blue = 0,
            brightness = 255,
            power = false,
            modifier = modifier
        )
        "ssd1306_oled" -> OledDisplayWidget(
            configured = component.state != com.rgbcontroller.core.components.model.ComponentState.NOT_CONFIGURED,
            text = "",
            hasData = false,
            modifier = modifier
        )
        else -> GenericComponentWidget(component = component, modifier = modifier)
        else -> GenericComponentWidget(component = component, modifier = modifier)
}

@Composable
fun GenericComponentWidget(
    component: ConfiguredComponent,
    modifier: Modifier = Modifier
) {
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(component.descriptor.name, style = MaterialTheme.typography.titleMedium)
            Text(component.descriptor.description, style = MaterialTheme.typography.bodySmall)
        }
    }
}
