package com.rgbcontroller.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val DarkPalette = darkColorScheme(
    primary = Color(0xFFD0B9FF),
    onPrimary = Color(0xFF2B1850),
    primaryContainer = Color(0xFF4B3B66),
    onPrimaryContainer = Color(0xFFF0E8FF),
    secondary = Color(0xFFBFC1D0),
    background = Color(0xFF17171C),
    onBackground = Color(0xFFF3F0F7),
    surface = Color(0xFF222128),
    onSurface = Color(0xFFF3F0F7),
    surfaceVariant = Color(0xFF302F39),
    onSurfaceVariant = Color(0xFFC6C3CE),
    outline = Color(0xFF706C7B),
    error = Color(0xFFFFB4AB)
)
private val LightPalette = lightColorScheme(
    primary = Color(0xFF61428E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADCFF),
    onPrimaryContainer = Color(0xFF29104D),
    background = Color(0xFFF8F7FB),
    onBackground = Color(0xFF201F26),
    surface = Color.White,
    onSurface = Color(0xFF201F26),
    surfaceVariant = Color(0xFFF0EDF5),
    onSurfaceVariant = Color(0xFF55515F),
    outline = Color(0xFF817A89)
)

/** Shared minimal matte theme; respects the system unless overridden in Settings. */
@Composable
fun RgbTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) DarkPalette else LightPalette, content = content)
}

@Composable
fun SectionHeader(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
