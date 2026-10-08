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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.ble.BleConnectionState
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.core.data.PreferencesRepository
import com.rgbcontroller.core.model.ColorUtils
import kotlinx.coroutines.launch

private data class NamedColor(
    val title: String, val hex: String, val favorite: Boolean = false
)

@Composable
fun PresetsScreen(bleManager: BleManager, preferencesRepository: PreferencesRepository) {
    val connected by bleManager.connectionState.collectAsState()
    val current by bleManager.rgbState.collectAsState()
    val stored by preferencesRepository.favoriteColors.collectAsState(initial = "")
    val scope = rememberCoroutineScope()
    val ready = connected == BleConnectionState.Ready
    val favorites = stored.split(',').map { it.trim() }
        .filter { ColorUtils.hexToRgb(it) != null }.distinct()
    val presets = listOf(
        NamedColor("Rojo", "#FF0000"), NamedColor("Verde", "#00FF00"),
        NamedColor("Azul", "#0000FF"), NamedColor("Blanco", "#FFFFFF"),
        NamedColor("Amarillo", "#FFFF00"), NamedColor("Cian", "#00FFFF"),
        NamedColor("Magenta", "#FF00FF"), NamedColor("Morado", "#800080"),
        NamedColor("Naranja", "#FF8000")
    )
    val cards = presets + favorites.map { NamedColor("Favorito", it, favorite = true) }
    fun apply(hex: String) {
        if (!ready) return
        val rgb = ColorUtils.hexToRgb(hex) ?: return
        bleManager.writeCommand(
            ColorUtils.serializeSetCommand(rgb.r, rgb.g, rgb.b, current?.brightness ?: 255)
        )
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Paleta de colores", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (ready) "Elige un color para aplicarlo al ESP32"
            else "Conecta el ESP32 antes de enviar colores",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = ready && current != null,
            onClick = {
                val s = current ?: return@Button
                val hex = ColorUtils.rgbToHex(s.red, s.green, s.blue)
                scope.launch {
                    preferencesRepository.saveFavoriteColors((favorites + hex).distinct().joinToString(","))
                }
            }
        ) { Text("Guardar color actual en favoritos") }
        Spacer(Modifier.height(14.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(cards) { preset ->
                val rgb = ColorUtils.hexToRgb(preset.hex) ?: return@items
                val uiColor = Color(rgb.r, rgb.g, rgb.b)
                Card(
                    Modifier.fillMaxWidth().clickable(enabled = ready) { apply(preset.hex) }
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Box(
                            Modifier.fillMaxWidth().height(48.dp)
                                .background(uiColor, RoundedCornerShape(10.dp))
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(preset.title, style = MaterialTheme.typography.titleSmall)
                        Text(preset.hex, style = MaterialTheme.typography.labelSmall)
                        if (preset.favorite) {
                            TextButton(onClick = {
                                scope.launch {
                                    preferencesRepository.saveFavoriteColors(
                                        favorites.filterNot { it == preset.hex }.joinToString(",")
                                    )
                                }
                            }) { Text("Quitar favorito") }
                        }
                    }
                }
            }
        }
    }
}
