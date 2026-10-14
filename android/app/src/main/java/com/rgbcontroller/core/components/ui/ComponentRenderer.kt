package com.rgbcontroller.core.components.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.components.model.ComponentMeasurement
import com.rgbcontroller.core.components.model.ComponentState
import com.rgbcontroller.core.components.model.ComponentTelemetryCodec
import com.rgbcontroller.core.components.model.ConfiguredComponent
import com.rgbcontroller.core.components.widgets.*

@Composable
fun ComponentRenderer(
    component: ConfiguredComponent,
    measurements: List<ComponentMeasurement>? = null,
    modifier: Modifier = Modifier
) {
    val active = ComponentTelemetryCodec.stateFor(component.id, measurements) == ComponentState.ACTIVE
    val find = { type: String -> ComponentTelemetryCodec.find(measurements.orEmpty(), type) }
    when (component.descriptor.id) {
        "dht22" -> {
            val humidity = find("humidity")?.takeIf { it.valid }?.value
            val temperature = find("temperature")?.takeIf { it.valid }?.value
            HumidityTemperatureWidget(
                humidity = humidity,
                temperature = temperature,
                hasData = active && humidity != null && temperature != null,
                modifier = modifier
            )
        }
        "soil_moisture" -> {
            val moisture = find("soil_moisture")?.takeIf { it.valid }?.value
            val raw = find("adc_raw")?.takeIf { it.valid }?.value?.toInt()
            SoilMoistureWidget(
                moisturePercent = moisture,
                rawAdc = raw,
                calibrated = moisture != null,
                modifier = modifier
            )
        }
        "led_rgb" -> RgbLedWidget(
            red = 0, green = 0, blue = 0, brightness = 255, power = false, modifier = modifier
        )
        "ssd1306_oled" -> OledDisplayWidget(
            configured = component.state != ComponentState.NOT_CONFIGURED,
            text = "",
            hasData = false,
            modifier = modifier
        )
        else -> GenericComponentWidget(component = component, modifier = modifier)
    }
}

@Composable
fun GenericComponentWidget(
    component: ConfiguredComponent,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(component.descriptor.name, style = MaterialTheme.typography.titleMedium)
            Text(component.descriptor.description, style = MaterialTheme.typography.bodySmall)
        }
    }
}
