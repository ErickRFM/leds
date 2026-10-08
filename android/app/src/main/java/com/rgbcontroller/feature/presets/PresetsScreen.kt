package com.rgbcontroller.feature.presets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.core.data.PreferencesRepository
import com.rgbcontroller.core.model.ColorUtils

data class PresetColor(val name: String, val r: Int, val g: Int, val b: Int, val color: Color)

@Composable
fun PresetsScreen(bleManager: BleManager, preferencesRepository: PreferencesRepository) {
    val presets = listOf(
        PresetColor("Rojo Puro", 255, 0, 0, Color.Red),
        PresetColor("Verde Puro", 0, 255, 0, Color.Green),
        PresetColor("Azul Puro", 0, 0, 255, Color.Blue),
        PresetColor("Blanco Cálido", 255, 200, 150, Color(255, 200, 150)),
        PresetColor("Amarillo", 255, 255, 0, Color.Yellow),
        PresetColor("Cian", 0, 255, 255, Color.Cyan),
        PresetColor("Magenta", 255, 0, 255, Color.Magenta),
        PresetColor("Morado", 128, 0, 128, Color(128, 0, 128)),
        PresetColor("Naranja", 255, 128, 0, Color(255, 128, 0))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Colores Predefinidos", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(presets) { preset ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .clickable {
                            bleManager.writeCommand(ColorUtils.serializeSetCommand(preset.r, preset.g, preset.b, 255))
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(preset.color)
                        )
                        Text(preset.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
