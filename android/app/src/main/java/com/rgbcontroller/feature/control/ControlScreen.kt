package com.rgbcontroller.feature.control

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.core.model.ColorUtils

@Composable
fun ControlScreen(bleManager: BleManager) {
    var red by remember { mutableStateOf(255f) }
    var green by remember { mutableStateOf(120f) }
    var blue by remember { mutableStateOf(0f) }
    var brightness by remember { mutableStateOf(255f) }
    var powerOn by remember { mutableStateOf(true) }

    val rInt = red.toInt()
    val gInt = green.toInt()
    val bInt = blue.toInt()
    val brightInt = brightness.toInt()

    val currentPreviewColor = if (powerOn) {
        val factor = brightInt / 255f
        Color(
            red = (rInt / 255f) * factor,
            green = (gInt / 255f) * factor,
            blue = (bInt / 255f) * factor
        )
    } else {
        Color.DarkGray
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Control RGB", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        // Color Preview Circle
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(currentPreviewColor)
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(ColorUtils.rgbToHex(rInt, gInt, bInt), style = MaterialTheme.typography.bodyLarge)

        Spacer(modifier = Modifier.height(16.dp))

        // Power Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Encendido / Apagado", style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = powerOn,
                onCheckedChange = { checked ->
                    powerOn = checked
                    if (checked) {
                        bleManager.writeCommand(ColorUtils.serializeOnCommand())
                    } else {
                        bleManager.writeCommand(ColorUtils.serializeOffCommand())
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Red Slider
        Text("Rojo: $rInt")
        Slider(
            value = red,
            onValueChange = {
                red = it
                if (powerOn) bleManager.writeCommand(ColorUtils.serializeSetCommand(it.toInt(), gInt, bInt, brightInt))
            },
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(thumbColor = Color.Red, activeTrackColor = Color.Red)
        )

        // Green Slider
        Text("Verde: $gInt")
        Slider(
            value = green,
            onValueChange = {
                green = it
                if (powerOn) bleManager.writeCommand(ColorUtils.serializeSetCommand(rInt, it.toInt(), bInt, brightInt))
            },
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(thumbColor = Color.Green, activeTrackColor = Color.Green)
        )

        // Blue Slider
        Text("Azul: $bInt")
        Slider(
            value = blue,
            onValueChange = {
                blue = it
                if (powerOn) bleManager.writeCommand(ColorUtils.serializeSetCommand(rInt, gInt, it.toInt(), brightInt))
            },
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(thumbColor = Color.Blue, activeTrackColor = Color.Blue)
        )

        // Brightness Slider
        Text("Brillo: $brightInt")
        Slider(
            value = brightness,
            onValueChange = {
                brightness = it
                if (powerOn) bleManager.writeCommand(ColorUtils.serializeSetCommand(rInt, gInt, bInt, it.toInt()))
            },
            valueRange = 0f..255f
        )
    }
}
