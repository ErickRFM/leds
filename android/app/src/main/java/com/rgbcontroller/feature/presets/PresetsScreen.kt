package com.rgbcontroller.feature.presets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import com.rgbcontroller.core.ui.SectionHeader
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.ble.BleConnectionState
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.core.data.PreferencesRepository
import com.rgbcontroller.core.model.RgbControlLink
import com.rgbcontroller.core.model.ColorUtils
import kotlinx.coroutines.launch

private data class NamedColor(
    val title: String, val hex: String, val favorite: Boolean = false
)

@Composable
fun PresetsScreen(bleManager: BleManager, preferencesRepository: PreferencesRepository, link: RgbControlLink? = null) {
    val connected by bleManager.connectionState.collectAsState()
    val bleCurrent by bleManager.rgbState.collectAsState()
    val current = if (link != null) link.state else bleCurrent
    val stored by preferencesRepository.favoriteColors.collectAsState(initial = "")
    val scope = rememberCoroutineScope()
    val ready = link?.ready ?: (connected == BleConnectionState.Ready)
    fun send(command: String) {
        if (link != null) link.sendCommand(command) else bleManager.writeCommand(command)
    }
    val favorites = stored.split(',').map { it.trim() }
        .filter { ColorUtils.hexToRgb(it) != null }.distinct()
    val presets = listOf(
        NamedColor("Rojo", "#FF0000"), NamedColor("Verde", "#00FF00"),
        NamedColor("Azul", "#0000FF"), NamedColor("Blanco", "#FFFFFF"),
        NamedColor("Amarillo", "#FFFF00"), NamedColor("Cian", "#00FFFF"),
        NamedColor("Magenta", "#FF00FF"), NamedColor("Morado", "#800080"),
        NamedColor("Naranja", "#FF8000")
    )
    // Avoid showing the same color twice when a built-in preset is favorited.
    val cards = presets.map { it.copy(favorite = it.hex in favorites) } +
        favorites.filterNot { hex -> presets.any { it.hex == hex } }
            .map { NamedColor("Personalizado", it, favorite = true) }
    fun apply(hex: String) {
        if (!ready) return
        val rgb = ColorUtils.hexToRgb(hex) ?: return
        send(
            ColorUtils.serializeSetCommand(rgb.r, rgb.g, rgb.b, current?.brightness ?: 255)
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 20.dp)) {
        SectionHeader("Colores", if (ready) "Tu paleta personal"
            else "Conecta el ESP32 para aplicar colores")
        Spacer(Modifier.height(20.dp))
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
            columns = GridCells.Adaptive(minSize = 144.dp),
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
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Box(
                            Modifier.fillMaxWidth().height(48.dp)
                                .background(uiColor, RoundedCornerShape(14.dp))
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(preset.title + if (preset.favorite) " ★" else "",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold)
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
