package com.rgbcontroller.core.components.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun RgbLedWidget(
    red: Int,
    green: Int,
    blue: Int,
    brightness: Int,
    power: Boolean,
    onColorChange: (Int, Int, Int) -> Unit = { _, _, _ -> },
    onBrightnessChange: (Int) -> Unit = {},
    onPowerToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("LED RGB", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Switch(checked = power, onCheckedChange = { onPowerToggle() })
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(red, green, blue))
                        .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
                Text("RGB(,,) Brillo:", style = MaterialTheme.typography.bodySmall)
            }
            if (power) {
                Slider(value = brightness.toFloat(), onValueChange = { onBrightnessChange(it.toInt()) }, valueRange = 0f..255f)
            }
        }
    }
}
